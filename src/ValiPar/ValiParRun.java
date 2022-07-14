package ValiPar;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

public final class ValiParRun {

	public ValiParRun() {

	}

	public void newExperiment() {

		File file = new File("./experiment");
		try {
			if (file.isDirectory()) {
				FileUtils.deleteDirectory(file);
			}
			FileUtils.forceMkdir(new File("experiment"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	public void createProject(int testID) {
		try {
			FileUtils.forceMkdir(new File("./experiment/test" + testID));

			Process process = new ProcessBuilder("valipar", "project", "--setup")
					.directory(new File("./experiment/test" + testID)).start();
			process.waitFor();

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("Failure to create the ValiPar project!");
		}

	}

	public void deleteProject(int testID) {
		try {
			Process process = new ProcessBuilder("valipar", "project", "--delete")
					.directory(new File("./experiment/test" + testID)).start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void newTestCase(Genotype<IntegerGene> testdata, int testID, String[] testSetup) {
		String data = testdata.chromosome().toString().replace("[", "").replace("]", "").replace(",", " ");
		String[] newtestCase = testSetup.clone();

		for (int i = 0; i < testSetup.length; i++) {
			newtestCase[i] = testSetup[i].replace("TESTDATA", data);
		}

		try {

			Process process = new ProcessBuilder(newtestCase).directory(new File("./experiment/test" + testID)).start();

			process.waitFor();

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void elementsGenerator(int testID) {
		try {
			Process process = Runtime.getRuntime().exec("valipar elem", null, new File("./experiment/test" + testID));
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void evaluation(int testID) {
		try {

			Process process = new ProcessBuilder("valipar", "eval").directory(new File("./experiment/test" + testID))
					.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static synchronized void execution(int testID) {

		try {
			Process process = new ProcessBuilder("valipar", "exec", "-t", "0", "-l", "10000")
					.directory(new File("./experiment/test" + testID)).start();
			process.waitFor();

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void instrumentation(int testID, ProcessBuilder instrumentation, File filesPath) {
		try {

			FileUtils.copyDirectory(filesPath, new File("./experiment/test" + testID),
					FileFilterUtils.and(FileFileFilter.FILE, FileFilterUtils.suffixFileFilter(".class")));

			Process process = instrumentation.directory(new File("./experiment/test" + testID)).start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		
		if (isEmpty(testID)) {
			instrumentation(testID,instrumentation, filesPath);
		}
		
		
	}

	public static synchronized boolean isPortinUse(int port) {

		try {
			(new DatagramSocket(port)).close();
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;

	}
	
	public boolean isEmpty(int testID){
		

		try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("./experiment/test"+testID+"/valipar/instrumented"))) {
			return !dirStream.iterator().hasNext();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return true; 
		}
		
		
		
	}

}
