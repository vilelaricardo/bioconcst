package Utilities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

public class BioConcST_FileReader {

	private String fileResult;

	public String fileReader(String path) { // File reader returns a file in String format

		if (new File(path).exists()) {
			try {
				fileResult = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		} else {
			System.out.println("The file is not exists");
			return null;
		}
		return fileResult;

	}
}
