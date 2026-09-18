package CoverageInst.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FixtureCompilerTest {

	@Test
	void compilesASingleClassToARealClassFile() throws Exception {
		File classFile = FixtureCompiler.compileOne("Hello",
				"public class Hello { public static void main(String[] a) { System.out.println(\"hi\"); } }");

		assertTrue(classFile.exists(), "expected " + classFile + " to exist after compilation");
	}

	@Test
	void compilesMultipleClassesTogether() throws Exception {
		Map<String, File> classes = FixtureCompiler.compile(Map.of(
				"A", "public class A { public static void main(String[] a) {} }",
				"B", "public class B { public static void main(String[] a) {} }"));

		assertTrue(classes.get("A").exists());
		assertTrue(classes.get("B").exists());
	}
}
