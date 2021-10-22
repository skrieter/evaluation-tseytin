package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import org.spldev.evaluation.tseytin.Parameters;
import org.spldev.evaluation.tseytin.Wrapper;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.formula.expression.io.FormulaFormatManager;
import org.spldev.formula.expression.transform.Transformer;
import org.spldev.formula.solver.RuntimeTimeoutException;
import org.spldev.util.data.Pair;
import org.spldev.util.io.FileHandler;
import org.spldev.util.job.Executor;
import org.spldev.util.logging.Logger;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Analysis implements Serializable {
	private static final long serialVersionUID = 1L;
	public static Analysis[] transformations = new Analysis[] {
		new Transform.TseytinZ3(),
		new Transform.TseytinSPLDev(),
		new Transform.DistribFeatureIDE(),
		new Transform.DistribSPLDev()
	};
	public static List<Pair<Class<?>, String[]>> analyses = new ArrayList<>();

	static {
		analyses.add(new Pair<>(Transform.class, new String[] { "TransformTime", "Variables", "Clauses" }));
		analyses.add(new Pair<>(SatFeatureIDE.class, new String[] { "SatTimeF", "SatF" }));
		analyses.add(new Pair<>(SatSPLDev.class, new String[] { "SatTimeS", "SatS" }));
		analyses.add(new Pair<>(CoreDeadFeatureIDE.class, new String[] { "CoreDeadTimeF", "CoreDeadHashF",
			"CoreDeadF" }));
		analyses.add(new Pair<>(CoreDeadSPLDev.class, new String[] { "CoreDeadTimeS", "CoreDeadHashS", "CoreDeadS" }));
		analyses.add(new Pair<>(AtomicSetFeatureIDE.class, new String[] { "AtomicSetTimeF", "AtomicSetHashF",
			"AtomicSetF" }));
		analyses.add(new Pair<>(AtomicSetSPLDev.class, new String[] { "AtomicSetTimeS", "AtomicSetHashS",
			"AtomicSetS" }));
		analyses.add(new Pair<>(SharpSat.class, new String[] { "SharpSatTime", "SharpSat" }));
		analyses.add(new Pair<>(CountAntom.class, new String[] { "CountAntomTime", "CountAntom" }));
	}

	public Parameters parameters;

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	static class Result<T> {
		Long timeNeeded;
		String md5;
		T result;

		public Result(Long timeNeeded, String md5, T result) {
			this.timeNeeded = timeNeeded;
			this.md5 = md5;
			this.result = result;
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

	public String[] getResultColumns() {
		return analyses.stream()
			.filter(analysisPair -> analysisPair.getKey().equals(this.getClass()))
			.findFirst().orElseThrow().getValue();
	}

	protected <T> T execute(Callable<T> method) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<T> future = executor.submit(method);
		try {
			return future.get(parameters.timeout, TimeUnit.MILLISECONDS);
		} catch (TimeoutException | ExecutionException | InterruptedException | RuntimeTimeoutException e) {
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
			return new Result<>(timeNeeded, null, transformedFormula);
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

	protected Path getTempPath(String suffix) {
		return Paths.get(parameters.tempPath).resolve(
			String.format("%s_%s_%d.%s",
				parameters.system.replaceAll("[./]", "_"),
				parameters.transformation, parameters.iteration, suffix));
	}

	protected Path getTempPath() {
		return getTempPath("dimacs");
	}

	protected boolean fileExists(Path path) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(path.toFile()));
			if (br.readLine() == null)
				return false;
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	protected void printResult(Object o) {
		System.out.println(Wrapper.RESULT_PREFIX + o);
	}

	protected void processFormulaResult(Result<Formula> result) {
		if (result != null) {
			printResult(result.timeNeeded);
			printResult(VariableMap.fromExpression(result.result).size());
			printResult(result.result.getChildren().size());
			writeFormula(result.result, getTempPath());
		}
	}

	protected void printResult(Result<?> result) {
		if (result != null) {
			printResult(result.timeNeeded);
			if (result.md5 != null)
				printResult(result.md5.substring(0, 6));
			printResult(result.result);
		}
	}

	private List<String> getActualFeatures(Stream<String> stream) {
		return stream.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("__Root__"))
			.filter(name -> !name.startsWith("k!"))
			.collect(Collectors.toList());
	}

	protected List<String> getActualFeatures(ModelRepresentation rep) {
		return getActualFeatures(rep.getVariables().getNames().stream());
	}

	protected List<String> getActualFeatures(CNF cnf) {
		return getActualFeatures(Arrays.stream(cnf.getVariables().getNames()));
	}

	protected String md5(List<String> list) {
		try {
			MessageDigest m = MessageDigest.getInstance("MD5");
			m.reset();
			m.update(list.toString().getBytes());
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1, digest);
			StringBuilder hash = new StringBuilder(bigInt.toString(16));
			while (hash.length() < 32) {
				hash.insert(0, "0");
			}
			return hash.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected String md5(String str) {
		return md5(Collections.singletonList(str));
	}

	abstract public void run() throws Exception;

	abstract static class Transformation extends Analysis {
		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	abstract static class FeatureIDEAnalysis extends Analysis {
		@Override
		public void run() throws Exception {
			if (fileExists(getTempPath())) {
				final IFeatureModel featureModel = FeatureModelManager.load(getTempPath());
				if (featureModel != null) {
					run(featureModel);
				}
			}
		}

		abstract void run(IFeatureModel featureModel) throws Exception;
	}

	abstract static class SPLDevAnalysis extends Analysis {
		@Override
		public void run() throws Exception {
			if (fileExists(getTempPath())) {
				final org.spldev.util.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
				if (rep.isPresent()) {
					run(rep.get());
				}
			}
		}

		abstract void run(ModelRepresentation modelRepresentation) throws Exception;
	}

	abstract static class ProcessAnalysis<T> extends Analysis {
		protected boolean useTimeout;

		@Override
		public void run() {
			final String[] command = getCommand();
			printResult(execute(() -> {
				final long localTime = System.nanoTime();
				Pair<T, String> result = getDefaultResult();
				Process process = null;
				ProcessBuilder processBuilder = new ProcessBuilder(command);
				try {
					process = processBuilder.start();
					final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
					boolean success;
					if (useTimeout)
						success = process.waitFor(parameters.timeout, TimeUnit.MILLISECONDS);
					else
						success = process.waitFor() == 0;
					if (success) {
						process = null;
						result = getResult(reader.lines());
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				} finally {
					if (process != null) {
						process.destroyForcibly();
					}
				}
				final long timeNeeded = System.nanoTime() - localTime;
				return new Result<>(timeNeeded, result.getValue(), result.getKey());
			}));
		}

		abstract String[] getCommand();

		abstract Pair<T, String> getDefaultResult();

		abstract Pair<T, String> getResult(Stream<String> lines);
	}
}
