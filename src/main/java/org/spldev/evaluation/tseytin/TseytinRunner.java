package org.spldev.evaluation.tseytin;

import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.CoreDeadAnalysis;
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
import java.util.concurrent.*;

public class TseytinRunner {
	static String modelPathName, modelFileName, tempPath;
	static int maximumNumberOfClauses, maximumLengthOfClauses, i;

	public static void main(String[] args) {
		if (args.length != 8)
			throw new RuntimeException("invalid usage");
		modelPathName = args[0];
		modelFileName = args[1];
		maximumNumberOfClauses = Integer.parseInt(args[2]);
		maximumLengthOfClauses = Integer.parseInt(args[3]);
		i = Integer.parseInt(args[4]);
		tempPath = args[5];
		String stage = args[6];
		long timeout = Long.parseLong(args[7]);

		if (stage.equals("model2cnf")) {
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(Paths.get(modelPathName));
			fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
			final Formula formula = fmReader.read(modelFileName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			final ModelRepresentation rep = new ModelRepresentation(formula);

			// localTime = System.nanoTime();
			// formula = Executor.run(new CNFTseytinTransformer(), formula).orElseThrow();
			// timeNeeded = System.nanoTime() - localTime;

			CountingCNFTseytinTransformer transformer = new CountingCNFTseytinTransformer(
				maximumNumberOfClauses, maximumLengthOfClauses);
			FormulaProvider.TseytinCNF formulaProvider = (c, m) -> Provider.convert(c,
				FormulaProvider.identifier, transformer, m);

			ExecutorService executor = Executors.newSingleThreadExecutor();
			Future<?> future = executor.submit(() -> {
				long localTime = System.nanoTime();
				Formula tseytinFormula = rep.get(formulaProvider);
				long timeNeeded = System.nanoTime() - localTime;
				printResult(timeNeeded);
				printResult(VariableMap.fromExpression(tseytinFormula).size());
				printResult(tseytinFormula.getChildren().size());
				// printResult("NA");
				// printResult("NA");
				printResult(transformer.getNumberOfTseytinTransformedClauses());
				printResult(transformer.getNumberOfTseytinTransformedConstraints());
				try {
					FileHandler.save(tseytinFormula, getTempPath(), new DIMACSFormat());
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			try {
				future.get(timeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
			}
			executor.shutdownNow();
		} else if (stage.equals("sat")) {
			Formula formula = FileHandler.load(getTempPath(),
					new DIMACSFormat()).get();
			if (formula == null) {
				return;
			}
			final ModelRepresentation rep = new ModelRepresentation(formula);

			ExecutorService executor = Executors.newSingleThreadExecutor();
			Future<?> future = executor.submit(() -> {
				long localTime = System.nanoTime();
				Boolean sat = new HasSolutionAnalysis().getResult(rep).get();
				long timeNeeded = System.nanoTime() - localTime;
				printResult(timeNeeded);
				printResult(sat);
			});
			try {
				future.get(timeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
			}
			executor.shutdownNow();
		} else if (stage.equals("core-dead")) {
			Formula formula = FileHandler.load(getTempPath(),
					new DIMACSFormat()).get();
			if (formula == null) {
				return;
			}
			final ModelRepresentation rep = new ModelRepresentation(formula);

			ExecutorService executor = Executors.newSingleThreadExecutor();
			Future<?> future = executor.submit(() -> {
				long localTime = System.nanoTime();
				new CoreDeadAnalysis().getResult(rep).get();
				long timeNeeded = System.nanoTime() - localTime;
				printResult(timeNeeded);
			});
			try {
				future.get(timeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
			}
			executor.shutdownNow();
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
