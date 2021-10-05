package org.spldev.evaluation.tseytin;

import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.FormulaProvider;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.formula.expression.io.parse.KConfigReaderFormat;
import org.spldev.util.Provider;
import org.spldev.util.io.FileHandler;
import org.spldev.util.io.format.FormatSupplier;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TseytinRunner {
	static String modelPathName, modelFileName, tempPath;
	static int maximumNumberOfClauses, maximumLengthOfClauses, i;

	public static void main(String[] args) {
		if (args.length != 7)
			throw new RuntimeException("invalid usage");
		modelPathName = args[0];
		modelFileName = args[1];
		maximumNumberOfClauses = Integer.parseInt(args[2]);
		maximumLengthOfClauses = Integer.parseInt(args[3]);
		i = Integer.parseInt(args[4]);
		tempPath = args[5];
		String stage = args[6];
		long localTime, timeNeeded;

		if (stage.equals("model2cnf")) {
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(Paths.get(modelPathName));
			fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
			Formula formula = fmReader.read(modelFileName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			final ModelRepresentation rep = new ModelRepresentation(formula);

//			System.out.println("===");
//			JavaSmtSolver.toTseitinFormula(formula);
//			System.out.println("===");

			CountingCNFTseytinTransformer transformer = new CountingCNFTseytinTransformer(
				maximumNumberOfClauses, maximumLengthOfClauses);
			FormulaProvider.TseytinCNF formulaProvider = (c, m) -> Provider.convert(c,
				FormulaProvider.identifier, transformer, m);
			localTime = System.nanoTime();
			formula = rep.get(formulaProvider);
			timeNeeded = System.nanoTime() - localTime;

			printResult(timeNeeded);
			printResult(VariableMap.fromExpression(formula).size());
			printResult(formula.getChildren().size());
			printResult(transformer.getNumberOfTseytinTransformedClauses());
			printResult(transformer.getNumberOfTseytinTransformedConstraints());
			try {
				FileHandler.save(formula, getTempPath(), new DIMACSFormat());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (stage.equals("sat")) {
			Formula formula = FileHandler.load(getTempPath(),
				new DIMACSFormat()).get();
			if (formula == null) {
				return;
			}
			final ModelRepresentation rep = new ModelRepresentation(formula);
			localTime = System.nanoTime();
			Boolean sat = new HasSolutionAnalysis().getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			printResult(timeNeeded);
			printResult(sat);
		} else {
			throw new RuntimeException("invalid stage");
		}
	}

	private static Path getTempPath() {
		return Paths.get(tempPath).resolve((String.format("%s_%s_%d_%d_%d", modelPathName, modelFileName,
			maximumNumberOfClauses, maximumLengthOfClauses, i)).replace("/", "_"));
	}

	static void printResult(Object o) {
		System.out.println("res=" + o);
	}
}