/* -----------------------------------------------------------------------------
 * Evaluation-Tseytin - Program for the evaluation of the Tseytin transformation.
 * Copyright (C) 2021  Sebastian Krieter, Elias Kuiter
 * 
 * This file is part of Evaluation-Tseytin.
 * 
 * Evaluation-Tseytin is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-Tseytin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Tseytin.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/ekuiter/evaluation-tseytin> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

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
	static Supplier<CountingCNFTseytinTransformer> transformerSupplier;

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
	static int maxLiterals, i;

	static long timeout;

	public static void main(String[] args) {
		if (args.length != 8) {
			throw new RuntimeException("invalid usage");
		}
		modelPathName = args[0];
		modelFileName = args[1];
		maxLiterals = Integer.parseInt(args[2]);
		i = Integer.parseInt(args[3]);
		tempPath = args[4];
		final String stage = args[5];
		timeout = Long.parseLong(args[6]);
		transformerSupplier = args[7].equals("baseline") ? BaselineCNFTransformer::new
			: CountingCNFTseytinTransformer::new;

		ExtensionLoader.load();

		switch (stage) {
		case "model2cnf": {
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(Paths.get(modelPathName));
			fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
			final Formula formula = fmReader.read(modelFileName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			final TransformResult result = execute(() -> transform(formula));
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
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			final Result<Boolean> result = execute(() -> sat(rep));
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(result.result);
			}
			break;
		}
		case "core": {
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			final Result<Integer> result = execute(() -> core(rep));
			if (result != null) {
				printResult(result.timeNeeded);
				printResult(result.result);
			}
			break;
		}
		case "sharpsat": {
			final ModelRepresentation rep = ModelRepresentation.load(getTempPath()).orElseThrow();
			final Result<BigInteger> result = execute(() -> sharpsat(rep));
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

	private static TransformResult transform(Formula formula) {
		final CountingCNFTseytinTransformer transformer = transformerSupplier.get();
		transformer.setMaximumNumberOfLiterals(maxLiterals);
		final long localTime = System.nanoTime();
		formula = Executor.run(transformer, formula).orElse(Logger::logProblems);
		final long timeNeeded = System.nanoTime() - localTime;
		return new TransformResult(formula, transformer.getNumberOfTseytinTransformedClauses(), transformer
			.getNumberOfTseytinTransformedConstraints(), timeNeeded);
	}

	private static Result<Boolean> sat(ModelRepresentation rep) {
		final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
		final long localTime = System.nanoTime();
		final Boolean sat = hasSolutionAnalysis.getResult(rep).get();
		final long timeNeeded = System.nanoTime() - localTime;
		return new Result<>(sat, timeNeeded);
	}

	private static Result<Integer> core(ModelRepresentation rep) {
		final CoreDeadAnalysis coreDeadAnalysis = new CoreDeadAnalysis();
		final long localTime = System.nanoTime();
		final LiteralList coreDead = coreDeadAnalysis.getResult(rep).orElseThrow();
		final long timeNeeded = System.nanoTime() - localTime;
		return new Result<>(coreDead.size(), timeNeeded);
	}

	private static Result<BigInteger> sharpsat(ModelRepresentation rep) {
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
		sb.append(maxLiterals);
		sb.append("_");
		sb.append(i);
		sb.append(".dimacs");
		return Paths.get(tempPath).resolve(sb.toString());
	}

	static void printResult(Object o) {
		System.out.println("res=" + o);
	}
}
