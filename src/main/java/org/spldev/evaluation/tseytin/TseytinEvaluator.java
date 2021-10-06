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

	protected static final ListProperty<Integer> maxNumProperty = new ListProperty<>("maxNum",
		Property.IntegerConverter);
	protected static final ListProperty<Integer> maxLenProperty = new ListProperty<>("maxLen",
		Property.IntegerConverter);

	protected CSVWriter writer, systemWriter;
	protected ProcessRunner processRunner;
	private String systemName;
	private int maxNumValue, maxLenValue;
	private Formula formula;
	private String[] results = new String[11];

	@Override
	public String getId() {
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
			Arrays.asList("ID", "MaxNumOfClauses", "MaxLenOfClauses", "Iteration",
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
		final List<Integer> maxNumValues = maxNumProperty.getValue();
		final List<Integer> maxLenValues = maxLenProperty.getValue();
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			systemName = config.systemNames.get(systemIndex);
			formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException("no feature model"));
			writeCSV(systemWriter, this::writeSystem);
			for (systemIteration = 0; systemIteration < config.systemIterations.getValue(); systemIteration++) {
				tabFormatter.setTabLevel(0);
				logSystem();
				tabFormatter.setTabLevel(1);
				int lastMaxLen = Integer.MAX_VALUE;
				boolean skipRemaining = false;

				maxNumValue = 0;
				maxLenValue = 0;
				transform();
				writeCSV(writer, this::writeResults);

				for (int i = 0; i < maxNumValues.size(); i++) {
					for (int j = 0; j < maxLenValues.size(); j++) {
						maxNumValue = maxNumValues.get(i);
						maxLenValue = maxLenValues.get(j);
						Arrays.fill(results, "NA");
						if (skipRemaining || (maxLenValue >= lastMaxLen)) {
							Logger.logInfo("Skipping for " + systemName + " " + maxNumValue + " " + maxLenValue);
						} else {
							transform();
							if ("0".equals(results[4])) {
								lastMaxLen = maxLenValue;
								if (j == 0) {
									skipRemaining = true;
								}
							}
						}
						writeCSV(writer, this::writeResults);
					}
				}
			}
		}
	}

	private void transform() {
		Logger.logInfo("Running for " + systemName + " " + maxNumValue + " " + maxLenValue);
		tabFormatter.setTabLevel(2);
		final TseytinAlgorithm algorithm = new TseytinAlgorithm(config.modelPath, systemName,
			maxNumValue, maxLenValue, systemIteration, config.tempPath, config.timeout.getValue());
		runAlgorithm(algorithm, "model2cnf", 0);
		runAlgorithm(algorithm, "sat", 5);
		runAlgorithm(algorithm, "core", 7);
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
		writer.addValue(maxNumValue);
		writer.addValue(maxLenValue);
		writer.addValue(systemIteration);
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
