package org.spldev.evaluation.tseytin;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.util.concurrent.*;

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
import org.spldev.util.job.Executor;
import org.spldev.util.logging.*;

public class TseytinRunner {

	private static class Result<T> {
		private final T result;
		private final long timeNeeded;

		public Result(T result, long timeNeeded) {
			this.result = result;
			this.timeNeeded = timeNeeded;
		}
	}

	private static class TransformResult {
		private final Formula formula;
		private final long timeNeeded;
		private final int numberOfTseytinTransformedClauses;
		private final int numberOfTseytinTransformedConstraints;

		public TransformResult(Formula formula, int numberOfTseytinTransformedClauses,
			int numberOfTseytinTransformedConstraints, long timeNeeded) {
			this.formula = formula;
			this.numberOfTseytinTransformedClauses = numberOfTseytinTransformedClauses;
			this.numberOfTseytinTransformedConstraints = numberOfTseytinTransformedConstraints;
			this.timeNeeded = timeNeeded;
		}
	}

	static String modelPathName, modelFileName, tempPath;
	static int maximumNumberOfClauses, maximumLengthOfClauses, i;

	static long timeout;

	public static void main(String[] args) {
		if (args.length != 8) {
			throw new RuntimeException("invalid usage");
		}
		modelPathName = args[0];
		modelFileName = args[1];
		maximumNumberOfClauses = Integer.parseInt(args[2]);
		maximumLengthOfClauses = Integer.parseInt(args[3]);
		i = Integer.parseInt(args[4]);
		tempPath = args[5];
		final String stage = args[6];
		timeout = Long.parseLong(args[7]);

		ExtensionLoader.load();

		switch (stage) {
		case "model2cnf": {

			final TransformResult result = execute(TseytinRunner::transform);
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(VariableMap.fromExpression(result.formula).size());
				printResult(result.formula.getChildren().size());
				printResult(result.numberOfTseytinTransformedClauses);
				printResult(result.numberOfTseytinTransformedConstraints);
				try {
					FileHandler.save(result.formula, getTempPath(), new DIMACSFormat());
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
			break;
		}
		case "sat": {
			final Result<Boolean> result = execute(TseytinRunner::sat);
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(result.result);
			}
			break;
		}
		case "core": {
			final Result<Integer> result = execute(TseytinRunner::core);
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(result.result);
			}
			break;
		}
		case "sharpsat": {
			final Result<BigInteger> result = execute(TseytinRunner::sharpsat);
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(result.result);
			}
			break;
		}
		default: {
			throw new RuntimeException("invalid stage");
		}
		}
	}

	private static TransformResult transform() {
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(Paths.get(modelPathName));
		fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
		Formula formula = fmReader.read(modelFileName)
			.orElseThrow(p -> new RuntimeException("no feature model"));

		final CountingCNFTseytinTransformer transformer = new CountingCNFTseytinTransformer(
			maximumNumberOfClauses, maximumLengthOfClauses);
		final long localTime = System.nanoTime();
		formula = Executor.run(transformer, formula).orElse(Logger::logProblems);
		final long timeNeeded = System.nanoTime() - localTime;
		return new TransformResult(formula, transformer.getNumberOfTseytinTransformedClauses(), transformer
			.getNumberOfTseytinTransformedConstraints(),
			timeNeeded);
	}

	private static Result<Boolean> sat() {
		final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
		final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
		final long localTime = System.nanoTime();
		final Boolean sat = hasSolutionAnalysis.getResult(rep).get();
		final long timeNeeded = System.nanoTime() - localTime;
		return new Result<>(sat, timeNeeded);
	}

	private static Result<Integer> core() {
		final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
		final CoreDeadAnalysis coreDeadAnalysis = new CoreDeadAnalysis();
		final long localTime = System.nanoTime();
		final LiteralList coreDead = coreDeadAnalysis.getResult(rep).orElseThrow();
		final long timeNeeded = System.nanoTime() - localTime;
		return new Result<>(coreDead.size(), timeNeeded);
	}

	private static Result<BigInteger> sharpsat() {
		final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
		final CountSolutionsAnalysis countSolutionsAnalysis = new CountSolutionsAnalysis();
		countSolutionsAnalysis.setTimeout((int) (Math.ceil(timeout / 1000.0)));
		final long localTime = System.nanoTime();
		final BigInteger sharpSat = countSolutionsAnalysis.getResult(rep).get();
		final long timeNeeded = System.nanoTime() - localTime;
		return new Result<>(sharpSat, timeNeeded);
	}

	private static <T> T execute(Callable<T> method) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<T> future = executor.submit(method);
		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (final TimeoutException e) {
			System.exit(0);
		} catch (ExecutionException | InterruptedException e) {
			e.printStackTrace();
			System.exit(0);
		} finally {
			executor.shutdownNow();
		}
		return null;
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
