package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Orchestrates one benchmark's real, native (no Docker, no ValiPar) execution
 * for CoverageInst: compile the benchmark's own .java sources once, instrument
 * every resulting .class once, then run each individual's test case in its
 * own isolated working directory - so concurrent evaluations (the whole
 * point of threadExecutors in the real GA) never share the address registry,
 * marker files, or trace logs ValiPar-style test<N> isolation already
 * established the need for.
 *
 * Deliberately self-contained: compiles the benchmark from its own .java
 * source into a private work directory rather than depending on the
 * benchmark's own committed .class files, which are the ValiPar path's
 * artifacts (a specific bytecode version ValiPar's own instrumentation
 * needs - see project_valipar_compat memory) and must not be disturbed.
 */
public final class CoverageInstRun {

	private final File instrumentedDir;
	private final File workDir;
	private final String javaExecutable;
	private final Map<String, ControlFlowGraph> flowGraphs;
	private final Map<String, String> syncEdgeBlocks;
	private final Map<String, Integer> branchPredicates;
	// Unique per JVM invocation, not just per test case: testId alone restarts
	// at 0 every time TestDataGeneration runs, and ValiparInitializer's
	// /tmp/.server<id>* coordination files (used by 003_token_ring_file) only
	// ever get cleaned up by a SUCCESSFUL run reaching ValiparInitializer.clean()
	// - a crashed or timed-out test leaves its files behind in /tmp forever.
	// Without this prefix, a later run reusing the same small testId range
	// collides with stale files from an earlier crashed run and fails outright
	// (observed: FileAlreadyExistsException on /tmp/.server0_0).
	private final String runInstanceId = Long.toHexString(System.nanoTime());

	public File instrumentedDir() {
		return instrumentedDir;
	}

	/** Key: "className#methodName" - see BasicBlockInstrumenter. */
	public Map<String, ControlFlowGraph> flowGraphs() {
		return flowGraphs;
	}

	/** Key: sync edge id, value: the basic-block id containing it - see BasicBlockInstrumenter. */
	public Map<String, String> syncEdgeBlocks() {
		return syncEdgeBlocks;
	}

	/** Key: block id, value: its recognized numeric-predicate opcode - see BranchDistance. */
	public Map<String, Integer> branchPredicates() {
		return branchPredicates;
	}

	private CoverageInstRun(File instrumentedDir, File workDir, Map<String, ControlFlowGraph> flowGraphs,
			Map<String, String> syncEdgeBlocks, Map<String, Integer> branchPredicates) {
		this.instrumentedDir = instrumentedDir;
		this.workDir = workDir;
		this.javaExecutable = ProcessHandle.current().info().command().orElse("java");
		this.flowGraphs = flowGraphs;
		this.syncEdgeBlocks = syncEdgeBlocks;
		this.branchPredicates = branchPredicates;
	}

	public static CoverageInstRun prepare(File benchmarkSourceDir, File workDir) throws IOException {
		File compileDir = new File(workDir, "compiled");
		File instrumentedDir = new File(workDir, "instrumented");
		Files.createDirectories(compileDir.toPath());
		Files.createDirectories(instrumentedDir.toPath());

		compile(benchmarkSourceDir, compileDir);
		instrumentAll(compileDir, instrumentedDir);

		// Second pass, over the sync-hook-instrumented output above: see
		// BasicBlockInstrumenter's own javadoc for why this runs separately
		// instead of being folded into ClassInstrumenter/instrumentAll.
		Map<String, ControlFlowGraph> flowGraphs = new LinkedHashMap<>();
		Map<String, String> syncEdgeBlocks = new LinkedHashMap<>();
		Map<String, Integer> branchPredicates = new LinkedHashMap<>();
		File[] classFiles = instrumentedDir.listFiles((dir, name) -> name.endsWith(".class"));
		if (classFiles != null) {
			for (File classFile : classFiles) {
				String className = classFile.getName().substring(0, classFile.getName().length() - ".class".length());
				BasicBlockInstrumenter.ClassResult result = BasicBlockInstrumenter.instrument(classFile, className);
				try (FileOutputStream out = new FileOutputStream(classFile)) {
					out.write(result.bytes);
				}
				flowGraphs.putAll(result.graphs);
				syncEdgeBlocks.putAll(result.syncEdgeBlocks);
				branchPredicates.putAll(result.branchPredicates);
			}
		}

		return new CoverageInstRun(instrumentedDir, workDir, flowGraphs, syncEdgeBlocks, branchPredicates);
	}

	private static void compile(File sourceDir, File outputDir) throws IOException {
		File[] sources = sourceDir.listFiles((dir, name) -> name.endsWith(".java"));
		if (sources == null || sources.length == 0) {
			throw new IOException("No .java sources found in " + sourceDir);
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException(
					"No system Java compiler available - CoverageInst needs to run under a JDK, not a JRE");
		}
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
			Iterable<? extends javax.tools.JavaFileObject> units = fileManager
					.getJavaFileObjectsFromFiles(List.of(sources));
			boolean ok = compiler.getTask(null, fileManager, null, List.of("--release", "25", "-d", outputDir.getPath()),
					null, units).call();
			if (!ok) {
				throw new IOException("Compilation failed for benchmark sources in " + sourceDir);
			}
		}
	}

	// Instrumenting every .class the compile produced (not just the ones
	// CoverageInst recognizes primitives in) means helper classes with no
	// sync calls at all (HelperClass, PeerState) just pass through
	// ClassInstrumenter unchanged - no need to track which classes are
	// "extra" separately from which ones matter.
	private static void instrumentAll(File compiledDir, File outputDir) throws IOException {
		File[] classFiles = compiledDir.listFiles((dir, name) -> name.endsWith(".class"));
		if (classFiles == null) {
			return;
		}
		for (File classFile : classFiles) {
			String className = classFile.getName().substring(0, classFile.getName().length() - ".class".length());
			byte[] bytes;
			try (FileInputStream in = new FileInputStream(classFile)) {
				bytes = in.readAllBytes();
			}
			ClassReader reader = new ClassReader(bytes);
			ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
			reader.accept(new ClassInstrumenter(writer, className, new ArrayList<>()), ClassReader.EXPAND_FRAMES);
			try (FileOutputStream out = new FileOutputStream(new File(outputDir, className + ".class"))) {
				out.write(writer.toByteArray());
			}
		}
	}

	public static final class ProcessLaunchSpec {
		public final int processId;
		public final String className;
		public final String[] args;

		public ProcessLaunchSpec(int processId, String className, String[] args) {
			this.processId = processId;
			this.className = className;
			this.args = args;
		}
	}

	public static final class TestCaseResult {
		public final File coverageDir;
		public final boolean allProcessesCompleted;

		TestCaseResult(File coverageDir, boolean allProcessesCompleted) {
			this.coverageDir = coverageDir;
			this.allProcessesCompleted = allProcessesCompleted;
		}
	}

	public TestCaseResult runTestCase(int testId, List<ProcessLaunchSpec> processes, int execTimeLimitMs)
			throws IOException, InterruptedException {
		return runTestCase(testId, processes, execTimeLimitMs, null);
	}

	/**
	 * Same as {@link #runTestCase(int, List, int)}, but when
	 * {@code replaySchedule} is non-null every launched process runs in
	 * controlled-execution mode: each sender blocks until it's its recorded
	 * turn before actually sending (see CoverageTracer/ReplaySchedule), so
	 * the run reproduces the exact sender-arrival order captured from a
	 * prior free run instead of whatever order the OS/scheduler happens to
	 * pick this time.
	 */
	public TestCaseResult runTestCase(int testId, List<ProcessLaunchSpec> processes, int execTimeLimitMs,
			File replaySchedule) throws IOException, InterruptedException {
		File testDir = new File(workDir, "test" + testId);
		File coverageDir = new File(testDir, "coverage");
		Files.createDirectories(coverageDir.toPath());

		String classpath = instrumentedDir.getAbsolutePath() + File.pathSeparator
				+ System.getProperty("java.class.path");

		List<Process> launched = new ArrayList<>();
		for (ProcessLaunchSpec spec : processes) {
			List<String> command = new ArrayList<>(List.of(javaExecutable, "-Djava.net.preferIPv4Stack=true",
					"-Dcoverage.dir=" + coverageDir.getAbsolutePath(), "-Dcoverage.processId=" + spec.processId));
			if (replaySchedule != null) {
				command.add("-Dcoverage.replaySchedule=" + replaySchedule.getAbsolutePath());
			}
			command.addAll(List.of("-cp", classpath, spec.className));
			command.addAll(List.of(spec.args));
			ProcessBuilder builder = new ProcessBuilder(command).directory(testDir).redirectErrorStream(true)
					.redirectOutput(new File(testDir, "p" + spec.processId + ".out"));
			// Some benchmarks (e.g. 003_token_ring_file) use ValiparInitializer
			// instead of HelperClass for address exchange, which keys its
			// /tmp/.server<id> coordination file on this env var rather than on
			// the process's working directory. runInstanceId makes this unique
			// across separate JVM invocations too, not just across test cases
			// within one run - see its field javadoc for why that matters.
			builder.environment().put("VALIPAR_EXECUTION_ID", runInstanceId + "-" + testId);
			launched.add(builder.start());
		}

		// A hung process (deadlock from a pathological genotype, same risk
		// ValiPar's own execTimeLimitMs/TLE handling exists for) must not
		// stall the whole GA - each process gets the full budget from when
		// it was started, and a timeout gets it force-killed and this test
		// case marked failed, matching FitnessFunction's "missing execution
		// trace treated as maximum distance" handling for the ValiPar path.
		long deadline = System.currentTimeMillis() + execTimeLimitMs;
		boolean allCompleted = true;
		for (Process process : launched) {
			long remaining = Math.max(0, deadline - System.currentTimeMillis());
			boolean finished = process.waitFor(remaining, TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				allCompleted = false;
			}
		}

		return new TestCaseResult(coverageDir, allCompleted);
	}
}
