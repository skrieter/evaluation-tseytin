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

import java.util.*;
import java.util.stream.*;

import org.spldev.evaluation.*;
import org.spldev.evaluation.process.*;
import org.spldev.evaluation.properties.*;
import org.spldev.evaluation.util.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.util.io.csv.*;
import org.spldev.util.logging.*;

/**
 * Evaluate the (hybrid) Tseytin transformation. This assumes that input
 * formulas are in a "partial" CNF (i.e., already a conjunction of some
 * formulas).
 */
public class TseytinEvaluator extends Evaluator {

	protected static final ListProperty<Integer> maxLiteralsProperty = new ListProperty<>("maxLiterals",
		Property.IntegerConverter);
	protected static final ListProperty<String> skipSharpSatProperty = new ListProperty<>("skipSharpSat",
		Property.StringConverter);
	protected static final Property<String> transformerProperty = new Property<>("transformer",
		Property.StringConverter);

	protected CSVWriter writer, systemWriter;
	protected ProcessRunner processRunner;
	private String systemName;
	private int maxLiterals;
	private Formula formula;
	private final String[] results = new String[11];
	private int algorithmIteration;

	@Override
	public String getName() {
		return "eval-tseytin";
	}

	@Override
	public String getDescription() {
		return "Evaluate the Tseytin transformation of formulas into CNF";
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		writer = addCSVWriter("evaluation.csv",
			Arrays.asList("ID", "MaxLiterals", "Iteration",
				"TransformTime", "Variables", "Clauses", "TseytinClauses", "TseytinConstraints",
				"SatTime", "Sat", "CoreTime", "Core", "SharpSatTime", "SharpSat"));
		systemWriter = addCSVWriter("systems.csv", Arrays.asList("ID", "System", "Features", "Constraints"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final int systemIndexEnd = config.systemNames.size();
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(config.modelPath);
		fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
		processRunner = new ProcessRunner();
		processRunner.setTimeout(config.timeout.getValue());
		final List<Integer> maxLiteralsValues = maxLiteralsProperty.getValue();
		for (algorithmIteration = 0; algorithmIteration < config.algorithmIterations.getValue(); algorithmIteration++) {
			for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
				systemName = config.systemNames.get(systemIndex);
				if (algorithmIteration == 0) {
					formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException("no feature model"));
					writeCSV(systemWriter, this::writeSystem);
				}
				for (systemIteration = 0; systemIteration < config.systemIterations.getValue(); systemIteration++) {
					tabFormatter.setTabLevel(0);
					logSystem();
					tabFormatter.setTabLevel(1);
					long lastMaxLiterals = Long.MAX_VALUE;

					for (Integer maxLiteralsValue : maxLiteralsValues) {
						maxLiterals = maxLiteralsValue;
						if (maxLiterals >= lastMaxLiterals) {
							Logger.logInfo("Skipping for " + systemName + " " + maxLiterals);
						} else {
							transform();
							if ("NA".equals(results[0]) || "0".equals(results[4])) {
								lastMaxLiterals = maxLiterals;
							}
							writeCSV(writer, this::writeResults);
						}
					}
				}
			}
		}
	}

	private void transform() {
		Logger.logInfo("Running for " + systemName + " " + maxLiterals);
		tabFormatter.setTabLevel(2);
		final TseytinAlgorithm algorithm = new TseytinAlgorithm(config.modelPath, systemName,
			maxLiterals, algorithmIteration + "_" + systemIteration, config.tempPath, config.timeout.getValue(),
			transformerProperty.getValue());
		Arrays.fill(results, "NA");
		runAlgorithm(algorithm, "model2cnf", 0);
		runAlgorithm(algorithm, "sat", 5);
		runAlgorithm(algorithm, "core", 7);
		if (skipSharpSatProperty.getValue().stream().noneMatch(s -> systemName.startsWith(s)))
			runAlgorithm(algorithm, "sharpsat", 9);
	}

	private void runAlgorithm(final TseytinAlgorithm algorithm, final String name, int resultIndex) {
		Logger.logInfo(name);
		algorithm.setStage(name);
		tabFormatter.incTabLevel();
		copyResults(processRunner.run(algorithm).getResult(), resultIndex);
		tabFormatter.decTabLevel();
	}

	private void writeResults(CSVWriter writer) {
		writer.addValue(systemIndex);
		writer.addValue(maxLiterals);
		writer.addValue(algorithmIteration + "_" + systemIteration);
		for (final String value : results) {
			writer.addValue(value);
		}
	}

	private void writeSystem(CSVWriter systemWriter) {
		systemWriter.addValue(systemIndex);
		systemWriter.addValue(systemName);
		systemWriter.addValue(VariableMap.fromExpression(formula).size());
		systemWriter.addValue(NormalForms.simplifyForNF(formula).getChildren().size());
	}

	private void copyResults(List<String> resultList, int start) {
		resultList = resultList.stream()
			.filter(line -> line.startsWith("res="))
			.map(line -> line.replace("res=", ""))
			.collect(Collectors.toList());
		for (final String value : resultList) {
			results[start++] = value;
		}
	}

}
