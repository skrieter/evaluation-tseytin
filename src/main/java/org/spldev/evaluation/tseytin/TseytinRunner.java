package org.spldev.evaluation.tseytin;

import java.io.*;
import java.nio.file.*;

import org.spldev.evaluation.util.*;
import org.spldev.formula.*;
import org.spldev.formula.analysis.sat4j.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.expression.io.parse.*;
import org.spldev.util.*;
import org.spldev.util.io.*;
import org.spldev.util.io.format.*;

public class TseytinRunner {
	public static void main(String[] args) {
		if (args.length != 6) {
			throw new RuntimeException("invalid usage");
		}
		final String modelPathName = args[0];
		final String modelFileName = args[1];
		final int maximumNumberOfClauses = Integer.parseInt(args[2]);
		final int maximumLengthOfClauses = Integer.parseInt(args[3]);
		final String tempPath = args[4];
		final String stage = args[5];
		long localTime, timeNeeded;

		if (stage.equals("model2cnf")) {
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(Paths.get(modelPathName));
			fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
			Formula formula = fmReader.read(modelFileName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			final ModelRepresentation rep = new ModelRepresentation(formula);

			final CountingCNFTseytinTransformer transformer = new CountingCNFTseytinTransformer(
				maximumNumberOfClauses, maximumLengthOfClauses);
			final FormulaProvider.TseytinCNF formulaProvider = (c, m) -> Provider.convert(c,
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
				FileHandler.save(formula, getTempPath(tempPath, modelPathName, modelFileName), new DIMACSFormat());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		} else if (stage.equals("sat")) {
			final Formula formula = FileHandler.load(getTempPath(tempPath, modelPathName, modelFileName),
				new DIMACSFormat()).orElseThrow();
			final ModelRepresentation rep = new ModelRepresentation(formula);
			localTime = System.nanoTime();
			final Boolean sat = new HasSolutionAnalysis().getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			printResult(timeNeeded);
			printResult(sat);
		} else {
			throw new RuntimeException("invalid stage");
		}
	}

	private static Path getTempPath(String tempPath, String modelPathName, String modelFileName) {
		return Paths.get(tempPath).resolve((modelPathName + "_" + modelFileName).replace("/", "_"));
	}

	static void printResult(Object o) {
		System.out.println("res=" + o);
	}
}
