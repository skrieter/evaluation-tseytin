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
import java.nio.file.*;
import java.util.*;

import org.spldev.evaluation.process.*;
import org.spldev.util.data.Pair;

public class EvaluationWrapper extends Algorithm<List<String>> {
	public static final String RESULT_PREFIX = "result: ";

	public enum TransformationAlgorithm {
		TSEYTIN_Z3, TSEYTIN_SPLDEV, DISTRIB_FEATUREIDE, DISTRIB_SPLDEV
	}

	public enum AnalysisAlgorithm {
		TRANSFORM, SAT_FEATUREIDE, SAT_SPLDEV, CORE_DEAD_FEATUREIDE, CORE_DEAD_SPLDEV, ATOMIC_SET_FEATUREIDE,
		ATOMIC_SET_SPLDEV, SHARP_SAT;

		String[] getResultColumns() {
			return algorithmResultColumns.stream()
				.filter(algorithmPair -> algorithmPair.getKey().equals(this))
				.findFirst().orElseThrow().getValue();
		}
	}

	public static TransformationAlgorithm[] transformationAlgorithms = new TransformationAlgorithm[] {
		TransformationAlgorithm.TSEYTIN_Z3, TransformationAlgorithm.TSEYTIN_SPLDEV,
		TransformationAlgorithm.DISTRIB_FEATUREIDE, TransformationAlgorithm.DISTRIB_SPLDEV
	};
	public static List<Pair<AnalysisAlgorithm, String[]>> algorithmResultColumns = new ArrayList<>();

	static {
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.TRANSFORM, new String[] { "TransformTime", "Variables",
			"Clauses" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.SAT_FEATUREIDE, new String[] { "SatTimeF",
			"SatF" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.SAT_SPLDEV, new String[] { "SatTimeS",
			"SatS" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.CORE_DEAD_FEATUREIDE, new String[] { "CoreDeadTimeF",
			"CoreDeadF" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.CORE_DEAD_SPLDEV, new String[] { "CoreDeadTimeS",
			"CoreDeadS" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.ATOMIC_SET_FEATUREIDE, new String[] { "AtomicSetTimeF",
			"AtomicSetF" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.ATOMIC_SET_SPLDEV, new String[] { "AtomicSetTimeS",
			"AtomicSetS" }));
		algorithmResultColumns.add(new Pair<>(AnalysisAlgorithm.SHARP_SAT, new String[] { "SharpSatTime",
			"SharpSat" }));
	}

	public static class Parameters implements Serializable {
		private static final long serialVersionUID = 1L;
		public String system;
		public String rootPath;
		public String modelPath;
		public int iteration;
		public String tempPath;
		public long timeout;
		public TransformationAlgorithm transformationAlgorithm;
		public AnalysisAlgorithm analysisAlgorithm;

		public Parameters(String system, String rootPath, String modelPath, int iteration, String tempPath,
			long timeout) {
			this.system = system;
			this.rootPath = rootPath;
			this.modelPath = modelPath;
			this.iteration = iteration;
			this.tempPath = tempPath;
			this.timeout = timeout;
		}

		public static Parameters read(Path path) {
			try {
				FileInputStream fileInputStream = new FileInputStream(path.toFile());
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
				Parameters parameters = (Parameters) objectInputStream.readObject();
				objectInputStream.close();
				return parameters;
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
			return String.format(
				"Parameters{rootPath='%s', modelPath='%s', iteration=%d, tempPath='%s', timeout=%d, transformationAlgorithm=%s, analysisAlgorithm=%s}",
				rootPath, modelPath, iteration, tempPath, timeout, transformationAlgorithm, analysisAlgorithm);
		}
	}

	private final Parameters parameters;
	private final ArrayList<String> results = new ArrayList<>();

	public EvaluationWrapper(Parameters parameters) {
		this.parameters = parameters;
	}

	public void setTransformationAlgorithm(TransformationAlgorithm transformationAlgorithm) {
		parameters.transformationAlgorithm = transformationAlgorithm;
	}

	public void setAnalysisAlgorithm(AnalysisAlgorithm analysisAlgorithm) {
		parameters.analysisAlgorithm = analysisAlgorithm;
	}

	@Override
	protected void addCommandElements() {
		Path parametersPath = Paths.get(parameters.tempPath).resolve("parameters.dat");
		parameters.write(parametersPath);
		addCommandElement("java");
		addCommandElement("-da");
		addCommandElement("-Xmx12g");
		addCommandElement("-cp");
		addCommandElement(System.getProperty("java.class.path") + ":ext-libs/*");
		addCommandElement(EvaluationRunner.class.getCanonicalName());
		addCommandElement(parametersPath.toString());
	}

	@Override
	public void postProcess() throws Exception {
		results.clear();
	}

	@Override
	public void readOutput(String line) throws Exception {
		if (line.startsWith(RESULT_PREFIX)) {
			results.add(line.replace(RESULT_PREFIX, "").trim());
		}
	}

	@Override
	public List<String> parseResults() {
		return new ArrayList<>(results);
	}

	@Override
	public String getName() {
		return "TseytinEvaluation";
	}

	@Override
	public String getParameterSettings() {
		return parameters.toString();
	}
}
