package CoverageInst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * One-off CLI to instrument a single .class file for CoverageInst tracing.
 *
 * java CoverageInst.InstrumentMain <input.class> <output.class>
 */
public class InstrumentMain {

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: InstrumentMain <input.class> <output.class>");
			System.exit(1);
		}

		byte[] original;
		try (FileInputStream in = new FileInputStream(new File(args[0]))) {
			original = in.readAllBytes();
		}

		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
		String className = reader.getClassName();
		List<SyncPoint> discovered = new ArrayList<>();
		ClassInstrumenter instrumenter = new ClassInstrumenter(writer, className.replace('/', '.'), discovered);
		reader.accept(instrumenter, ClassReader.EXPAND_FRAMES);

		try (FileOutputStream out = new FileOutputStream(new File(args[1]))) {
			out.write(writer.toByteArray());
		}

		System.out.println("Instrumented " + args[0] + " -> " + args[1] + " (" + discovered.size()
				+ " sync points found)");
		for (SyncPoint point : discovered) {
			System.out.println("  " + point);
		}
	}
}
