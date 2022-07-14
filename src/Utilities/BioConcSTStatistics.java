package Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.genetics.ListPopulation;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer.PopulationSize;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory;

import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.util.ISeq;

public class BioConcSTStatistics {

	private List<ISeq<Phenotype<IntegerGene, Double>>> bestList;
	private List<ArrayList<Double>> coverageList;
	private Selector<IntegerGene, Double> survivorsSelector;
	private List<ISeq<Phenotype<IntegerGene, Double>>> bestPop;
	private int generations;
	private int executions;
	private int populationSize;
	public BioConcSTStatistics(List<ISeq<Phenotype<IntegerGene, Double>>> bestList,
			List<ISeq<Phenotype<IntegerGene, Double>>> bestPop, List<ArrayList<Double>> coverageList,
			Selector<IntegerGene, Double> survivorsSelector, int generations, int executions, int populationSize) {

		this.bestList = bestList;
		this.coverageList = coverageList;
		this.survivorsSelector = survivorsSelector;
		this.bestPop = bestPop;
		this.generations = generations;
		this.executions = executions;
		this.populationSize=populationSize;
	}

	public void store() {
		XSSFWorkbook workbook = null;
		File file = new File("GcdMasterSlave.xlsx");
		if (file.exists()) {
			InputStream input;
			try {
				input = new FileInputStream(file);
				workbook = (XSSFWorkbook) XSSFWorkbookFactory.create(input);
			} catch (EncryptedDocumentException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			try {
				workbook = new XSSFWorkbook();

			} catch (EncryptedDocumentException e) {
				e.printStackTrace();
			}
		}
		XSSFSheet sheet = workbook.createSheet(survivorsSelector.toString().replaceAll("Selector.*$", ""));
		build(sheet);
		write(workbook, file);
	}

	private Map<String, Object[]> build(XSSFSheet sheet) {

		// Create Title row
		List<Title> titleRow = new ArrayList<Title>();
		for (int i = 0; i < bestList.size(); i++) {
			titleRow.add(new Title(i));
		}

		int rowNum = 0;
		int column = 0;
		Row row = sheet.createRow(rowNum);

		for (Title titleCol : titleRow) {
			Cell cell = row.createCell(column++);
			cell.setCellValue(titleCol.fitness);
			cell = row.createCell(column++);
			cell.setCellValue(titleCol.coverage);
			cell = row.createCell(column++);
			cell.setCellValue(titleCol.population);
		}

		List<ArrayList<Data>> data = new ArrayList<ArrayList<Data>>();

		for (int i = 0; i < generations; i++)
			data.add(new ArrayList<Data>());

		for (int i = 0; i < bestList.size(); i++) {
			int aux = 0;
			for (int j = 0; j < bestList.get(i).size(); j++) {

				data.get(aux)
						.add(new Data(bestList.get(i).get(j).fitness().toString(),
								coverageList.get(i).get(j).toString(),
								executions > i && populationSize > j ? bestPop.get(i).get(j).genotype().chromosome().toString() : ""));
				aux++;
			}

		}

		for (int i = 0; i < data.size(); i++) {
			System.out.println("Ricardo: " + data.get(i));
		}

		for (ArrayList<Data> rows : data) {
			column = 0;
			row = sheet.createRow(++rowNum);
			for (Data rowData : rows) {
				Cell cell = row.createCell(column);
				cell.setCellValue(rowData.fitness);
				cell = row.createCell(++column);
				cell.setCellValue(rowData.coverage);
				cell = row.createCell(++column);
				cell.setCellValue(rowData.population);
				column++;
			}

		}

		return null;
	}

	private void write(XSSFWorkbook workbook, File file) {
		try {

			if (file.exists()) {
				FileOutputStream fileOut = new FileOutputStream(file);
				workbook.write(fileOut);
				fileOut.close();
				System.out.println("Results written successfully on disk.");
			} else {

				FileOutputStream fileOut = new FileOutputStream(file);
				workbook.write(fileOut);
				fileOut.close();
				System.out.println("Results written successfully on disk.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class Title {
	public String fitness;
	public String coverage;
	public String population;

	public Title(int execution) {
		super();
		this.fitness = "Fitness: " + execution;
		this.coverage = "Coverage: " + execution;
		this.population = "Test Suite: " + execution;
	}

}

class Data {
	public String fitness;
	public String coverage;
	public String population;

	public Data(String fitness, String coverage, String population) {
		super();
		this.fitness = fitness;
		this.coverage = coverage;
		this.population = population;
	}

	@Override
	public String toString() {
		return "Data [fitness=" + fitness + ", coverage=" + coverage + ", population=" + population + "]";
	}

}
