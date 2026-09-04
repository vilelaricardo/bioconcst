package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/** Shared helper: run the same ASM pass ClassInstrumenter uses, just to collect the SyncPoints of a class file without writing it anywhere. */
public final class ClassScanner {

	private ClassScanner() {
	}

	public static List<SyncPoint> scan(File classFile, String className) throws IOException {
		byte[] bytes;
		try (FileInputStream in = new FileInputStream(classFile)) {
			bytes = in.readAllBytes();
		}
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
		List<SyncPoint> discovered = new ArrayList<>();
		reader.accept(new ClassInstrumenter(writer, className, discovered), ClassReader.EXPAND_FRAMES);
		return discovered;
	}

	/**
	 * Builds one ProcessInstance from every class that actually runs inside
	 * that process - not always just one (see ProcessInstance javadoc).
	 */
	public static ProcessInstance scanProcess(File classDir, int processId, String role, List<String> classNames)
			throws IOException {
		List<SyncPoint> points = new ArrayList<>();
		for (String className : classNames) {
			points.addAll(scan(new File(classDir, className + ".class"), className));
		}
		return new ProcessInstance(processId, role, points);
	}
}
