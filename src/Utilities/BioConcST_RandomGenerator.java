package Utilities;

import java.util.Random;

public class BioConcST_RandomGenerator {

	public synchronized static int randomIntegerGenerator(int min, int max) {
		int number;
		Random generator = new Random();
		number = generator.nextInt((max - min) + 1) + min;

		return number;
	}

	public synchronized static String randomStringGenerator(int lenght) {
		String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "abcdefghijklmnopqrstuvwxyz";
		String string = "";
		Random generator = new Random();
		for (int i = 0; i < lenght; i++) {
			string = string + (AlphaNumericString.charAt(generator.nextInt(AlphaNumericString.length())));
		}
		return string;
	}
}
