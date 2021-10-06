package org.spldev.evaluation.tseytin;

import java.io.*;
import java.math.*;
import java.nio.file.*;

import org.spldev.evaluation.util.*;
import org.spldev.formula.*;
import org.spldev.formula.analysis.sat4j.*;
import org.spldev.formula.analysis.sharpsat.CountSolutionsAnalysis;
import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.*;
import org.spldev.util.extension.*;
import org.spldev.util.io.*;
import org.spldev.util.job.*;
import org.spldev.util.logging.*;

public class TseytinRunner {
	static String modelPathName, modelFileName, tempPath;
	static int maximumNumberOfClauses, maximumLengthOfClauses, i;

	public static void main(String[] args) {
		if (args.length != 7) {
			throw new RuntimeException("invalid usage");
		}
		modelPathName = args[0];
		modelFileName = args[1];
		maximumNumberOfClauses = Integer.parseInt(args[2]);
		maximumLengthOfClauses = Integer.parseInt(args[3]);
		i = Integer.parseInt(args[4]);
		tempPath = args[5];
		final String stage = args[6];

		long localTime, timeNeeded;

		ExtensionLoader.load();

		switch (stage) {
		case "model2cnf": {
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(Paths.get(modelPathName));
			fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
			Formula formula = fmReader.read(modelFileName)
				.orElseThrow(p -> new RuntimeException("no feature model"));

			final CountingCNFTseytinTransformer transformer = new CountingCNFTseytinTransformer(
				maximumNumberOfClauses, maximumLengthOfClauses);
			localTime = System.nanoTime();
			formula = Executor.run(transformer, formula).orElse(Logger::logProblems);
			timeNeeded = System.nanoTime() - localTime;

			printResult(timeNeeded);
			printResult(VariableMap.fromExpression(formula).size());
			printResult(formula.getChildren().size());
//			printResult("NA");
//			printResult("NA");
			printResult(transformer.getNumberOfTseytinTransformedClauses());
			printResult(transformer.getNumberOfTseytinTransformedConstraints());
			try {
				FileHandler.save(formula, getTempPath(), new DIMACSFormat());
			} catch (final IOException e) {
				e.printStackTrace();
			}
			break;
		}
		case "sat": {
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			localTime = System.nanoTime();
			final Boolean sat = new HasSolutionAnalysis().getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			printResult(timeNeeded);
			printResult(sat);
			break;
		}
		case "core": {
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			localTime = System.nanoTime();
			final LiteralList coreDead = new CoreDeadAnalysis().getResult(rep).orElseThrow();
			timeNeeded = System.nanoTime() - localTime;
			printResult(timeNeeded);
			printResult(coreDead.size());
			break;
		}
		case "sharpsat": {
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			localTime = System.nanoTime();
			final CountSolutionsAnalysis countSolutionsAnalysis = new CountSolutionsAnalysis();
			final BigInteger sharpSat = countSolutionsAnalysis.getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			printResult(timeNeeded);
			printResult(sharpSat);
			break;
		}
		default: {
			throw new RuntimeException("invalid stage");
		}
		}
	}

	private static Path getTempPath() {
		final StringBuilder sb = new StringBuilder();
		sb.append(modelPathName.replaceAll("[/]", "_"));
		sb.append("_");
		sb.append(modelFileName.replaceAll("[./]", "_"));
		sb.append("_");
		sb.append(maximumNumberOfClauses);
		sb.append("_");
		sb.append(maximumLengthOfClauses);
		sb.append("_");
		sb.append(i);
		sb.append(".dimacs");
		return Paths.get(tempPath).resolve(sb.toString());
	}

	static void printResult(Object o) {
		System.out.println("res=" + o);
	}
}
