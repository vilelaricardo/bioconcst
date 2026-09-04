package CoverageInst.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles inline Java source into real .class files for tests, using the
 * exact same javac invocation CoverageInstRun.compile() uses on real
 * benchmarks (--release 25, javax.tools compiler API) - so instrumentation
 * tests exercise real javac-emitted bytecode idioms (invokedynamic string
 * concat, stack map frames, local variable slot layout) instead of hand-built
 * ASM byte arrays that wouldn't reproduce the bugs those idioms caused.
 */
public final class FixtureCompiler {

	private FixtureCompiler() {
	}

	/** Compiles a single top-level public class. Source must declare `public class <className>`. */
	public static File compileOne(String className, String source) throws IOException {
		return compile(Map.of(className, source)).get(className);
	}

	/**
	 * Compiles several top-level public classes (e.g. a sender + a receiver)
	 * into one output dir, keyed by simple class name - each source's own
	 * package declaration (if any) still determines where javac places its
	 * .class file under that dir, so callers needing the dir itself (e.g. to
	 * point a URLClassLoader at it) should use {@link #compileToOutputDir}.
	 */
	public static Map<String, File> compile(Map<String, String> sourcesByClassName) throws IOException {
		File outDir = compileToOutputDir(sourcesByClassName);
		Map<String, File> result = new LinkedHashMap<>();
		for (String className : sourcesByClassName.keySet()) {
			File classFile = findClassFile(outDir, className);
			if (classFile == null) {
				throw new IOException("Compiled output for " + className + " not found under " + outDir);
			}
			result.put(className, classFile);
		}
		return result;
	}

	/** Finds a compiled .class file by simple name anywhere under dir (handles package subdirectories). */
	public static File findClassFile(File dir, String simpleClassName) {
		return findClassFileRecursive(dir, simpleClassName);
	}

	/** Same compilation as {@link #compile}, returning the output directory root instead of per-class file lookups. */
	public static File compileToOutputDir(Map<String, String> sourcesByClassName) throws IOException {
		File srcDir = Files.createTempDirectory("coverageinst-fixture-src").toFile();
		File outDir = Files.createTempDirectory("coverageinst-fixture-out").toFile();

		List<File> sourceFiles = new ArrayList<>();
		for (Map.Entry<String, String> entry : sourcesByClassName.entrySet()) {
			File sourceFile = new File(srcDir, entry.getKey() + ".java");
			Files.writeString(sourceFile.toPath(), entry.getValue());
			sourceFiles.add(sourceFile);
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("No system Java compiler available - tests need to run under a JDK, not a JRE");
		}
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
			Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
			boolean ok = compiler
					.getTask(null, fileManager, null, List.of("--release", "25", "-d", outDir.getPath()), null, units)
					.call();
			if (!ok) {
				throw new IOException("Fixture compilation failed for " + sourcesByClassName.keySet());
			}
		}
		return outDir;
	}

	// Package declarations put the .class file in a matching subdirectory
	// (e.g. "CoverageInst/CoverageTracer.class") regardless of where the
	// source .java file itself lived - search for it instead of assuming
	// it's flat under outDir.
	private static File findClassFileRecursive(File dir, String simpleClassName) {
		File[] entries = dir.listFiles();
		if (entries == null) {
			return null;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				File found = findClassFileRecursive(entry, simpleClassName);
				if (found != null) {
					return found;
				}
			} else if (entry.getName().equals(simpleClassName + ".class")) {
				return entry;
			}
		}
		return null;
	}
}
