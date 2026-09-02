package ValiPar;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Pool of long-lived ValiPar Docker containers used to run "valipar exec"
 * in parallel. Each container gets its own network namespace, which is what
 * lets several executions run at once instead of contending for the fixed
 * port ValiPar's exec binds on the host.
 *
 * Containers are started once (lazily, sized to the number of available
 * cores) with the whole "./experiment" directory bind-mounted, and reused
 * for every test case via "docker exec -w /work/test<id> ...". Starting a
 * fresh container per call ("docker run") was measured to cost more than
 * the exec call itself; reusing already-running containers avoids that.
 */
public final class ValiParContainerPool {

	private static final String DOCKER_IMAGE = "bioconcst-valipar:0.1";
	private static final String WORKER_NAME_PREFIX = "valipar-worker-";

	private static volatile ValiParContainerPool instance;

	private final int size;
	private final BlockingQueue<String> idleWorkers;

	private ValiParContainerPool(File experimentDir) {
		this.size = Runtime.getRuntime().availableProcessors();
		this.idleWorkers = new ArrayBlockingQueue<>(size);
		startWorkers(experimentDir.getAbsoluteFile());
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	// experimentDir is only used to start the pool on the first call; later
	// calls return the already-running pool.
	public static ValiParContainerPool getInstance(File experimentDir) {
		if (instance == null) {
			synchronized (ValiParContainerPool.class) {
				if (instance == null) {
					instance = new ValiParContainerPool(experimentDir);
				}
			}
		}
		return instance;
	}

	private void startWorkers(File experimentDir) {
		for (int i = 0; i < size; i++) {
			String name = WORKER_NAME_PREFIX + i;
			runQuietly("docker", "rm", "-f", name);
			try {
				new ProcessBuilder("docker", "run", "-d", "--name", name, "-v",
						experimentDir + ":/work", "--entrypoint", "tail", DOCKER_IMAGE, "-f", "/dev/null").start()
						.waitFor();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			idleWorkers.add(name);
		}
	}

	public void execute(int testID) {
		String worker;
		try {
			worker = idleWorkers.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		try {
			Process process = new ProcessBuilder("docker", "exec", "-w", "/work/test" + testID, worker, "valipar",
					"exec", "-t", "0", "-l", "10000").start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		} finally {
			idleWorkers.add(worker);
		}
	}

	public void shutdown() {
		for (int i = 0; i < size; i++) {
			runQuietly("docker", "rm", "-f", WORKER_NAME_PREFIX + i);
		}
	}

	private void runQuietly(String... command) {
		try {
			new ProcessBuilder(command).start().waitFor();
		} catch (IOException | InterruptedException e) {
			// best-effort cleanup, nothing to recover here
		}
	}
}
