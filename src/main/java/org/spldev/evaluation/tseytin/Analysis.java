package org.spldev.evaluation.tseytin;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormatCNF;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.AtomicSetAnalysis;
import org.spldev.formula.analysis.sat4j.CoreDeadAnalysis;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.clauses.LiteralList;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.formula.expression.io.FormulaFormatManager;
import org.spldev.formula.expression.transform.CNFTransformer;
import org.spldev.formula.expression.transform.Transformer;
import org.spldev.formula.solver.javasmt.CNFTseytinTransformer;
import org.spldev.util.data.Pair;
import org.spldev.util.io.FileHandler;
import org.spldev.util.job.Executor;
import org.spldev.util.logging.Logger;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

abstract class Analysis implements Runnable, Serializable {
	private static final long serialVersionUID = 1L;
	public static Analysis[] transformations = new Analysis[] {
		new Analysis.TseytinZ3(),
		new Analysis.TseytinSPLDev(),
		new Analysis.DistribFeatureIDE(),
		new Analysis.DistribSPLDev()
	};
	public static List<Pair<Class<?>, String[]>> analyses = new ArrayList<>();

	static {
		analyses.add(new Pair<>(Transform.class, new String[] { "TransformTime", "Variables", "Clauses" }));
		analyses.add(new Pair<>(SatFeatureIDE.class, new String[] { "SatTimeF", "SatF" }));
		analyses.add(new Pair<>(SatSPLDev.class, new String[] { "SatTimeS", "SatS" }));
		analyses.add(new Pair<>(CoreDeadFeatureIDE.class, new String[] { "CoreDeadTimeF", "CoreDeadF" }));
		analyses.add(new Pair<>(CoreDeadSPLDev.class, new String[] { "CoreDeadTimeS", "CoreDeadS" }));
		analyses.add(new Pair<>(AtomicSetFeatureIDE.class, new String[] { "AtomicSetTimeF", "AtomicSetF" }));
		analyses.add(new Pair<>(AtomicSetSPLDev.class, new String[] { "AtomicSetTimeS", "AtomicSetS" }));
		analyses.add(new Pair<>(SharpSat.class, new String[] { "SharpSatTime", "SharpSat" }));
		analyses.add(new Pair<>(CountAntom.class, new String[] { "CountAntomTime", "CountAntom" }));
	}

	Parameters parameters;

	protected void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	static class Result<T> extends Pair<Long, T> {
		public Result(Long timeNeeded, T result) {
			super(timeNeeded, result);
		}
	}

	public static Analysis read(Path path) {
		try {
			FileInputStream fileInputStream = new FileInputStream(path.toFile());
			ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
			Analysis analysis = (Analysis) objectInputStream.readObject();
			objectInputStream.close();
			return analysis;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void write(Path path) {
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(path.toFile());
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
			objectOutputStream.writeObject(this);
			objectOutputStream.flush();
			objectOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{" +
			"parameters=" + parameters +
			'}';
	}

	String[] getResultColumns() {
		return analyses.stream()
			.filter(analysisPair -> analysisPair.getKey().equals(this.getClass()))
			.findFirst().orElseThrow().getValue();
	}

	protected <T> T execute(Callable<T> method) {
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

	protected Result<Formula> executeTransformer(Formula formula, Transformer transformer) {
		return execute(() -> {
			final long localTime = System.nanoTime();
			Formula transformedFormula = Executor.run(transformer, formula).orElse(Logger::logProblems);
			final long timeNeeded = System.nanoTime() - localTime;
			return new Result<>(timeNeeded, transformedFormula);
		});
	}

	protected Formula readFormula(Path path) {
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(Paths.get(parameters.rootPath));
		fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
		return fmReader.read(path.toString()).orElseThrow(p -> new RuntimeException("no feature model"));
	}

	protected void writeFormula(Formula formula, Path path) {
		try {
			FileHandler.save(formula, path, new DIMACSFormat());
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	protected Path getTempPath() {
		return Paths.get(parameters.tempPath).resolve(
			String.format("%s_%s_%d.dimacs",
				parameters.system.replaceAll("[./]", "_"),
				parameters.transformation, parameters.iteration));
	}

	protected void printResult(Object o) {
		System.out.println(Wrapper.RESULT_PREFIX + o);
	}

	protected void processFormulaResult(Result<Formula> result) {
		if (result != null) {
			printResult(result.getKey());
			printResult(VariableMap.fromExpression(result.getValue()).size());
			printResult(result.getValue().getChildren().size());
			writeFormula(result.getValue(), getTempPath());
		}
	}

	protected void printResult(Result<?> result) {
		if (result != null) {
			printResult(result.getKey());
			printResult(result.getValue());
		}
	}

	protected List<String> getNonTseytinFeatures(ModelRepresentation rep) {
		return rep.getVariables().getNames().stream()
			.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("k!"))
			.collect(Collectors.toList());
	}

	protected List<String> getNonTseytinFeatures(CNF cnf) {
		return Arrays.stream(cnf.getVariables().getNames())
			.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("k!"))
			.collect(Collectors.toList());
	}

	abstract static class Transformation extends Analysis {
		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	abstract static class FeatureIDEAnalysis extends Analysis {
		@Override
		public void run() {
			if (Files.exists(getTempPath())) {
				final IFeatureModel featureModel = FeatureModelManager.load(getTempPath());
				if (featureModel != null) {
					run(featureModel);
				}
			}
		}

		abstract void run(IFeatureModel featureModel);
	}

	abstract static class SPLDevAnalysis extends Analysis {
		@Override
		public void run() {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			if (rep.isPresent()) {
				run(rep.get());
			}
		}

		abstract void run(ModelRepresentation modelRepresentation);
	}

	public static class TseytinZ3 extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			processFormulaResult(executeTransformer(formula, new CNFTseytinTransformer()));
		}
	}

	public static class TseytinSPLDev extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			CNFTransformer transformer = new CNFTransformer();
			transformer.setMaximumNumberOfClauses(0);
			transformer.setMaximumLengthOfClauses(0);
			transformer.setMaximumNumberOfLiterals(0);
			processFormulaResult(executeTransformer(formula, transformer));
		}
	}

	public static class DistribFeatureIDE extends Transformation {
		@Override
		public void run() {
			final IFeatureModel featureModel = FeatureModelManager
				.load(Paths.get(parameters.rootPath).resolve(parameters.modelPath));
			if (featureModel != null) {
				Analysis.Result<CNF> result = execute(() -> {
					final long localTime = System.nanoTime();
					CNF cnf = new FeatureModelFormula(featureModel).getCNF();
					final long timeNeeded = System.nanoTime() - localTime;
					return new Analysis.Result<>(timeNeeded, cnf);
				});
				if (result != null) {
					printResult(result.getKey());
					printResult(result.getValue().getVariables().size());
					printResult(result.getValue().getClauses().size());
					de.ovgu.featureide.fm.core.io.manager.FileHandler.save(getTempPath(), result.getValue(),
						new DIMACSFormatCNF());
				}
			}
		}
	}

	public static class DistribSPLDev extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			CNFTransformer transformer = new CNFTransformer();
			transformer.setMaximumNumberOfClauses(Integer.MAX_VALUE);
			transformer.setMaximumLengthOfClauses(Integer.MAX_VALUE);
			transformer.setMaximumNumberOfLiterals(Integer.MAX_VALUE);
			processFormulaResult(executeTransformer(formula, transformer));
		}
	}

	public static class Transform extends Analysis { // todo: always call simplify for NF?
		@Override
		public void run() {
			parameters.transformation.run();
		}
	}

	public static class SatFeatureIDE extends FeatureIDEAnalysis {
		@Override
		public void run(IFeatureModel featureModel) {
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

	public static class SatSPLDev extends SPLDevAnalysis {
		@Override
		public void run(ModelRepresentation rep) {
			final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
			printResult(execute(() -> {
				final long localTime = System.nanoTime();
				final Boolean sat = hasSolutionAnalysis.getResult(rep).orElseThrow();
				final long timeNeeded = System.nanoTime() - localTime;
				return new Result<>(timeNeeded, sat);
			}));
		}
	}

	public static class CoreDeadFeatureIDE extends FeatureIDEAnalysis {
		@Override
		public void run(IFeatureModel featureModel) {
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

	public static class CoreDeadSPLDev extends SPLDevAnalysis {
		@Override
		public void run(ModelRepresentation rep) {
			final CoreDeadAnalysis coreDeadAnalysis = new CoreDeadAnalysis();
			// todo: we can calculate this for ALL features, or only the non-Tseytin
			// features. But it should be comparable
			// to the atomic sets! (is there any difference anyway?)
			// coreDeadAnalysis.setVariables(LiteralList.getVariables(rep.getVariables(),
			// getNotTseytinFeatures(rep)));
			printResult(execute(() -> {
				final long localTime = System.nanoTime();
				LiteralList coreDead = coreDeadAnalysis.getResult(rep).orElseThrow();
				final long timeNeeded = System.nanoTime() - localTime;
				coreDead = coreDead.retainAll(LiteralList.getLiterals(rep.getVariables(),
					getNonTseytinFeatures(rep)));
				return new Result<>(timeNeeded, coreDead.size());
			}));
		}
	}

	public static class AtomicSetFeatureIDE extends FeatureIDEAnalysis {
		@Override
		public void run(IFeatureModel featureModel) {
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

	public static class AtomicSetSPLDev extends SPLDevAnalysis {
		@Override
		public void run(ModelRepresentation rep) {
			final AtomicSetAnalysis atomicSetAnalysis = new AtomicSetAnalysis();
			// todo: maybe only calculate this for non-tseytin variables?
			printResult(execute(() -> {
				final long localTime = System.nanoTime();
				List<LiteralList> atomicSets = atomicSetAnalysis.getResult(rep).orElseThrow();
				final long timeNeeded = System.nanoTime() - localTime;
				LiteralList nonTseytinFeatures = LiteralList.getLiterals(rep.getVariables(),
					getNonTseytinFeatures(rep));
				atomicSets = atomicSets.stream()
					.map(atomicSet -> atomicSet.retainAll(nonTseytinFeatures))
					.filter(atomicSet -> !atomicSet.isEmpty())
					.collect(Collectors.toList());
				return new Result<>(timeNeeded, atomicSets.size());
			}));
		}
	}

	public static class SharpSat extends Analysis { // todo call sharp sat directly?
		@Override
		public void run() {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			if (rep.isPresent()) {
				final String[] command = new String[6];
				command[0] = "ext-libs/sharpSAT";
				command[1] = "-noCC";
				command[2] = "-noIBCP";
				command[3] = "-t";
				command[4] = String.valueOf((int) (Math.ceil(parameters.timeout) / 1000.0));
				command[command.length - 1] = getTempPath().toString();

				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					BigInteger sharpSat = BigInteger.valueOf(-1);
					Process process = null;
					ProcessBuilder processBuilder = new ProcessBuilder(command);
					try {
						process = processBuilder.start();
						final BufferedReader reader = new BufferedReader(new InputStreamReader(process
							.getInputStream()));
						final int exitValue = process.waitFor();
						if (exitValue == 0) {
							process = null;
							sharpSat = reader.lines().findFirst().map(BigInteger::new).orElse(BigInteger.ZERO);
						}
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					} finally {
						if (process != null) {
							process.destroyForcibly();
						}
					}
					final long timeNeeded = System.nanoTime() - localTime;
					return new Result<>(timeNeeded, sharpSat);
				}));
			}
		}
	}

	public static class CountAntom extends Analysis { // todo call sharp sat directly?
		@Override
		public void run() {
			final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
			if (rep.isPresent()) {
				final String[] command = new String[2];
				command[0] = "ext-libs/countAntom";
				// command[1] = "--doTseitin=no";
				command[command.length - 1] = getTempPath().toString();

				printResult(execute(() -> {
					final long localTime = System.nanoTime();
					BigInteger sharpSat = BigInteger.valueOf(-1);
					Process process = null;
					ProcessBuilder processBuilder = new ProcessBuilder(command);
					try {
						process = processBuilder.start();
						final BufferedReader reader = new BufferedReader(new InputStreamReader(process
							.getInputStream()));
						final boolean success = process.waitFor(parameters.timeout, TimeUnit.MILLISECONDS);
						if (success) {
							process = null;
							sharpSat = reader.lines()
								.filter(line -> line.startsWith("c model count"))
								.map(line -> line.split(":")[1].trim())
								.map(BigInteger::new)
								.findFirst()
								.orElse(BigInteger.ZERO);
						}
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					} finally {
						if (process != null) {
							process.destroyForcibly();
						}
					}
					final long timeNeeded = System.nanoTime() - localTime;
					return new Result<>(timeNeeded, sharpSat);
				}));
			}
		}
	}
}
