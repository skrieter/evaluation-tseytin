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

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.init.FMCoreLibrary;
import de.ovgu.featureide.fm.core.init.LibraryManager;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormatCNF;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.AtomicSetAnalysis;
import org.spldev.formula.analysis.sat4j.CoreDeadAnalysis;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.analysis.sharpsat.CountSolutionsAnalysis;
import org.spldev.formula.clauses.LiteralList;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.formula.expression.io.FormulaFormatManager;
import org.spldev.formula.expression.transform.CNFTransformer;
import org.spldev.formula.expression.transform.Transformer;
import org.spldev.formula.solver.javasmt.CNFTseytinTransformer;
import org.spldev.util.data.Pair;
import org.spldev.util.extension.ExtensionLoader;
import org.spldev.util.io.FileHandler;
import org.spldev.util.job.Executor;
import org.spldev.util.logging.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class EvaluationRunner {
	private static EvaluationWrapper.Parameters parameters;

	private static class Result<T> extends Pair<Long, T> {
		public Result(Long timeNeeded, T result) {
			super(timeNeeded, result);
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			throw new RuntimeException("invalid usage");
		}
		ExtensionLoader.load();
		LibraryManager.registerLibrary(FMCoreLibrary.getInstance());
		FMFormatManager.getInstance().addExtension(new KconfigReaderFormat());
		parameters = EvaluationWrapper.Parameters.read(Paths.get(args[0]));
		Objects.requireNonNull(parameters);
		System.out.println(parameters);

		if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.TRANSFORM) { // todo: always call
																								// simplify for NF?
			if (parameters.transformationAlgorithm == EvaluationWrapper.TransformationAlgorithm.TSEYTIN_Z3) {
				Formula formula = readFormula(Paths.get(parameters.modelPath));
				processFormulaResult(executeTransformer(formula, new CNFTseytinTransformer()));
			} else if (parameters.transformationAlgorithm == EvaluationWrapper.TransformationAlgorithm.TSEYTIN_SPLDEV) {
				Formula formula = readFormula(Paths.get(parameters.modelPath));
				CNFTransformer transformer = new CNFTransformer();
				transformer.setMaximumNumberOfClauses(0);
				transformer.setMaximumLengthOfClauses(0);
				transformer.setMaximumNumberOfLiterals(0);
				processFormulaResult(executeTransformer(formula, transformer));
			} else if (parameters.transformationAlgorithm == EvaluationWrapper.TransformationAlgorithm.DISTRIB_FEATUREIDE) {
				final IFeatureModel featureModel = FeatureModelManager.load(Paths.get(parameters.rootPath).resolve(
					parameters.modelPath));
				if (featureModel != null) {
					Result<CNF> result = execute(() -> {
						final long localTime = System.nanoTime();
						CNF cnf = new FeatureModelFormula(featureModel).getCNF();
						final long timeNeeded = System.nanoTime() - localTime;
						return new Result<>(timeNeeded, cnf);
					});
					if (result != null) {
						printResult(result.getKey());
						printResult(result.getValue().getVariables().size());
						printResult(result.getValue().getClauses().size());
						de.ovgu.featureide.fm.core.io.manager.FileHandler.save(getTempPath(), result.getValue(),
							new DIMACSFormatCNF());
					}
				}
			} else if (parameters.transformationAlgorithm == EvaluationWrapper.TransformationAlgorithm.DISTRIB_SPLDEV) {
				Formula formula = readFormula(Paths.get(parameters.modelPath));
				CNFTransformer transformer = new CNFTransformer();
				transformer.setMaximumNumberOfClauses(Integer.MAX_VALUE);
				transformer.setMaximumLengthOfClauses(Integer.MAX_VALUE);
				transformer.setMaximumNumberOfLiterals(Integer.MAX_VALUE);
				processFormulaResult(executeTransformer(formula, transformer));
			} else {
				throw new RuntimeException("invalid algorithm");
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.SAT_FEATUREIDE) {
			if (Files.exists(getTempPath())) {
				final IFeatureModel featureModel = FeatureModelManager.load(getTempPath());
				if (featureModel != null) {
					CNF cnf = new FeatureModelFormula(featureModel).getCNF(); // todo: can this be done faster?
					printResult(execute(() -> {
						final long localTime = System.nanoTime();
						Boolean sat = null;
						try {
							sat = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.HasSolutionAnalysis(cnf)
								.analyze(new NullMonitor<>());
						} catch (Exception e) {
							e.printStackTrace();
						}
						final long timeNeeded = System.nanoTime() - localTime;
						return new Result<>(timeNeeded, sat);
					}));
				}
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.SAT_SPLDEV) {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
			if (rep.isPresent()) {
				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					final Boolean sat = hasSolutionAnalysis.getResult(rep.get()).orElseThrow();
					final long timeNeeded = System.nanoTime() - localTime;
					return new Result<>(timeNeeded, sat);
				}));
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.CORE_DEAD_FEATUREIDE) {
			if (Files.exists(getTempPath())) {
				final IFeatureModel featureModel = FeatureModelManager.load(getTempPath());
				if (featureModel != null) {
					CNF cnf = new FeatureModelFormula(featureModel).getCNF(); // todo: can this be done faster?
					printResult(execute(() -> {
						final long localTime = System.nanoTime();
						LiteralSet coreDead = null;
						try {
							coreDead = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis(cnf)
								.analyze(new NullMonitor<>());
						} catch (Exception e) {
							e.printStackTrace();
						}
						final long timeNeeded = System.nanoTime() - localTime;
						if (coreDead == null)
							return null;
						coreDead = coreDead.retainAll(cnf.getVariables().convertToLiterals(getNonTseytinFeatures(cnf),
							true,
							true));
						// todo: recalculate variables
						return new Result<>(timeNeeded, coreDead.size());
					}));
				}
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.CORE_DEAD_SPLDEV) {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			final CoreDeadAnalysis coreDeadAnalysis = new CoreDeadAnalysis();
			// todo: we can calculate this for ALL features, or only the non-Tseytin
			// features. But it should be comparable
			// to the atomic sets! (is there any difference anyway?)
			// coreDeadAnalysis.setVariables(LiteralList.getVariables(rep.getVariables(),
			// getNotTseytinFeatures(rep)));
			if (rep.isPresent()) {
				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					LiteralList coreDead = coreDeadAnalysis.getResult(rep.get()).orElseThrow();
					final long timeNeeded = System.nanoTime() - localTime;
					coreDead = coreDead.retainAll(LiteralList.getLiterals(rep.get().getVariables(),
						getNonTseytinFeatures(rep.get())));
					return new Result<>(timeNeeded, coreDead.size());
				}));
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.ATOMIC_SET_FEATUREIDE) {
			if (Files.exists(getTempPath())) {
				final IFeatureModel featureModel = FeatureModelManager.load(getTempPath());
				if (featureModel != null) {
					CNF cnf = new FeatureModelFormula(featureModel).getCNF(); // todo: can this be done faster?
					printResult(execute(() -> {
						final long localTime = System.nanoTime();
						List<LiteralSet> atomicSets = null;
						try {
							atomicSets = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.AtomicSetAnalysis(cnf)
								.analyze(new NullMonitor<>());
						} catch (Exception e) {
							e.printStackTrace();
						}
						final long timeNeeded = System.nanoTime() - localTime;
						if (atomicSets == null)
							return null;
						LiteralSet nonTseytinFeatures = cnf.getVariables().convertToLiterals(getNonTseytinFeatures(cnf),
							true, true);
						atomicSets = atomicSets.stream()
							.map(atomicSet -> atomicSet.retainAll(nonTseytinFeatures))
							.filter(atomicSet -> !atomicSet.isEmpty())
							.collect(Collectors.toList());
						return new Result<>(timeNeeded, atomicSets.size());
					}));
				}
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.ATOMIC_SET_SPLDEV) {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			final AtomicSetAnalysis atomicSetAnalysis = new AtomicSetAnalysis();
			// todo: maybe only calculate this for non-tseytin variables?
			if (rep.isPresent()) {
				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					List<LiteralList> atomicSets = atomicSetAnalysis.getResult(rep.get()).orElseThrow();
					final long timeNeeded = System.nanoTime() - localTime;
					LiteralList nonTseytinFeatures = LiteralList.getLiterals(rep.get().getVariables(),
						getNonTseytinFeatures(rep.get()));
					atomicSets = atomicSets.stream()
						.map(atomicSet -> atomicSet.retainAll(nonTseytinFeatures))
						.filter(atomicSet -> !atomicSet.isEmpty())
						.collect(Collectors.toList());
					return new Result<>(timeNeeded, atomicSets.size());
				}));
			}
		} else if (parameters.analysisAlgorithm == EvaluationWrapper.AnalysisAlgorithm.SHARP_SAT) { // todo: call
																									// directly
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			final CountSolutionsAnalysis countSolutionsAnalysis = new CountSolutionsAnalysis();
			countSolutionsAnalysis.setTimeout((int) (Math.ceil(parameters.timeout) / 1000.0));
			if (rep.isPresent()) {
				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					final BigInteger sharpSat = countSolutionsAnalysis.getResult(rep.get()).get();
					final long timeNeeded = System.nanoTime() - localTime;
					return new Result<>(timeNeeded, sharpSat);
				}));
			}
		} else {
			throw new RuntimeException("invalid algorithm");
		}
	}

	private static List<String> getNonTseytinFeatures(ModelRepresentation rep) {
		return rep.getVariables().getNames().stream()
			.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("k!"))
			.collect(Collectors.toList());
	}

	private static List<String> getNonTseytinFeatures(CNF cnf) {
		return Arrays.stream(cnf.getVariables().getNames())
			.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("k!"))
			.collect(Collectors.toList());
	}

	// todo: indeterminate analysis is currently not implemented correctly, AFAIK
//    private static Result<Integer> indeterminate(ModelRepresentation rep) {
//        final IndeterminateAnalysis indeterminateAnalysis = new IndeterminateAnalysis();
//        final long localTime = System.nanoTime();
//        LiteralList indeterminate = indeterminateAnalysis.getResult(rep).orElseThrow();
//        final long timeNeeded = System.nanoTime() - localTime;
//        System.err.printf("got %d/%d indeterminate Tseytin features%n",
//                indeterminate.retainAll(LiteralList.getLiterals(rep.getVariables(), getTseytinFeatures(rep))).size(),
//                getTseytinFeatures(rep).size());
//        indeterminate = indeterminate.retainAll(LiteralList.getLiterals(rep.getVariables(), getNotTseytinFeatures(
//                rep)));
//        return new Result<>(indeterminate.size(), timeNeeded);
//    }

	private static <T> T execute(Callable<T> method) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<T> future = executor.submit(method);
		try {
			return future.get(parameters.timeout, TimeUnit.MILLISECONDS);
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

	private static Result<Formula> executeTransformer(Formula formula, Transformer transformer) {
		return execute(() -> {
			final long localTime = System.nanoTime();
			Formula transformedFormula = Executor.run(transformer, formula).orElse(Logger::logProblems);
			final long timeNeeded = System.nanoTime() - localTime;
			return new Result<>(timeNeeded, transformedFormula);
		});
	}

	private static Formula readFormula(Path path) {
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(Paths.get(parameters.rootPath));
		fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
		return fmReader.read(path.toString()).orElseThrow(p -> new RuntimeException("no feature model"));
	}

	private static void writeFormula(Formula formula, Path path) {
		try {
			FileHandler.save(formula, path, new DIMACSFormat());
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private static Path getTempPath() {
		return Paths.get(parameters.tempPath).resolve(
			String.format("%s_%s_%d.dimacs",
				parameters.system.replaceAll("[./]", "_"),
				parameters.transformationAlgorithm, parameters.iteration));
	}

	static void printResult(Object o) {
		System.out.println(EvaluationWrapper.RESULT_PREFIX + o);
	}

	private static void processFormulaResult(Result<Formula> result) {
		if (result != null) {
			printResult(result.getKey());
			printResult(VariableMap.fromExpression(result.getValue()).size());
			printResult(result.getValue().getChildren().size());
			writeFormula(result.getValue(), getTempPath());
		}
	}

	private static void printResult(Result<?> result) {
		if (result != null) {
			printResult(result.getKey());
			printResult(result.getValue());
		}
	}
}
