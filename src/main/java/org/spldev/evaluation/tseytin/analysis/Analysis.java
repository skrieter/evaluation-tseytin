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
		analyses.add(new Pair<>(SharpSatSharpSat.class, new String[] { "SharpSatTimeS", "SharpSatHashS",
			"SharpSatS" }));
		analyses.add(new Pair<>(SharpSatCountAntom.class, new String[] { "SharpSatTimeC", "SharpSatHashC",
			"SharpSatC" }));
	}

	public Parameters parameters;

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	static class Result<T> {
		Long timeNeeded;
		T payload;
		String md5;

		public Result(Long timeNeeded, T payload, String md5) {
			this.timeNeeded = timeNeeded;
			this.payload = payload;
			this.md5 = md5;
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

	protected <T> Result<T> execute(Callable<T> method) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Future<Result<T>> future = executor.submit(() -> {
			T payload = null;
			final long localTime = System.nanoTime();
			try {
				payload = method.call();
			} catch (Exception e) {
				e.printStackTrace();
			}
			final long timeNeeded = System.nanoTime() - localTime;
			return payload == null ? null : new Result<>(timeNeeded, payload, null);
		});
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
		return execute(() -> Executor.run(transformer, formula).orElse(Logger::logProblems));
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
			printResult(VariableMap.fromExpression(result.payload).size());
			printResult(result.payload.getChildren().size());
			writeFormula(result.payload, getTempPath());
		}
	}

	protected void printResult(Result<?> result) {
		if (result != null) {
			printResult(result.timeNeeded);
			if (result.md5 != null)
				printResult(result.md5.substring(0, 6));
			printResult(result.payload);
		}
	}

	private List<String> getActualFeatures(Stream<String> stream) {
		return stream.filter(name -> name != null && !name.startsWith("__temp__"))
			.filter(name -> !name.startsWith("__Root__"))
			.filter(name -> !name.startsWith("k!"))
			.filter(name -> !name.startsWith("|"))
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
				final org.spldev.util.data.Result<ModelRepresentation> rep = ModelRepresentation.load(getTempPath());
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
			if (fileExists(getTempPath())) {
				final String[] command = getCommand();
				Result<T> result = execute(() -> {
					T payload = getDefaultResult();
					Process process = null;
					ProcessBuilder processBuilder = new ProcessBuilder(command);
					try {
						process = processBuilder.start();
						final BufferedReader reader = new BufferedReader(new InputStreamReader(process
							.getInputStream()));
						boolean success;
						if (useTimeout)
							success = process.waitFor(parameters.timeout, TimeUnit.MILLISECONDS);
						else
							success = process.waitFor() == 0;
						if (success) {
							process = null;
							payload = getPayload(reader.lines());
						}
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					} finally {
						if (process != null) {
							process.destroyForcibly();
						}
					}
					return payload;
				});
				if (result == null)
					return;
				result.md5 = getMd5(result.payload);
				printResult(result);
			}
		}

		abstract String[] getCommand();

		abstract T getDefaultResult();

		abstract T getPayload(Stream<String> lines);

		abstract String getMd5(T payload);
	}
}
