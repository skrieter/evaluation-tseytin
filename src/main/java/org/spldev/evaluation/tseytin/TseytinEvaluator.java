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

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.process.ProcessRunner;
import org.spldev.evaluation.process.Result;
import org.spldev.evaluation.properties.ListProperty;
import org.spldev.evaluation.properties.Property;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.parse.KConfigReaderFormat;
import org.spldev.formula.expression.transform.NormalForms;
import org.spldev.util.data.Pair;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluate the (hybrid) Tseytin transformation. This assumes that input
 * formulas are in a "partial" CNF (i.e., already a conjunction of some
 * formulas).
 */
public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer, systemWriter;
	protected static final ListProperty<Integer> maxNum = new ListProperty<>("maxNum", Property.IntegerConverter);
	protected static final ListProperty<Integer> maxLen = new ListProperty<>("maxLen", Property.IntegerConverter);
	ProcessRunner processRunner;
	int maxNumValue, maxLenValue;
	HashMap<Pair<Integer, Integer>, List<List<String>>> cutoffPoints;

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
				"SatTime", "Sat",
				// "CoreDeadTime",
				"Inferred"));
		systemWriter = addCSVWriter("systems.csv", Arrays.asList("ID", "System", "Features", "Constraints"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final int systemIndexEnd = config.systemNames.size();
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(config.modelPath);
		fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
		processRunner = new ProcessRunner();
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			final String systemName = config.systemNames.get(systemIndex);
			logSystem();
			Formula formula = fmReader.read(systemName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			systemWriter.createNewLine();
			systemWriter.addValue(systemIndex);
			systemWriter.addValue(systemName);
			systemWriter.addValue(VariableMap.fromExpression(formula).size());
			systemWriter.addValue(NormalForms.simplifyForNF(formula).getChildren().size());
			systemWriter.flush();

			List<String> tseytinResults = null;
			cutoffPoints = new HashMap<>();
			for (int _maxNumValue : maxNum.getValue()) {
				maxNumValue = _maxNumValue;
				for (int _maxLenValue : maxLen.getValue()) {
					maxLenValue = _maxLenValue;
					if (!(maxNumValue == 0 && maxLenValue == 0) && (maxNumValue == 0 || maxLenValue == 0)) {
						for (int i = 0; i < config.systemIterations.getValue(); i++)
							skipEvaluate(maxNumValue, maxLenValue, i, tseytinResults);
					} else if (belowCutoffPoint().isEmpty()) {
						for (int i = 0; i < config.systemIterations.getValue(); i++) {
							List<String> resultList = evaluate(systemName, maxNumValue, maxLenValue, i);
							if ((resultList.get(0).equals("NA") || resultList.get(4).equals("0"))) {
								if (i == 0) {
									List<List<String>> res = new ArrayList<>();
									res.add(resultList);
									cutoffPoints.put(new Pair<>(maxNumValue, maxLenValue), res);
								} else
									cutoffPoints.get(new Pair<>(maxNumValue, maxLenValue)).add(resultList);
							}
							if (maxNumValue == 0 && maxLenValue == 0)
								tseytinResults = resultList;
						}
					} else {
						for (int i = 0; i < config.systemIterations.getValue(); i++)
							skipEvaluate(maxNumValue, maxLenValue, i, cutoffPoints.get(belowCutoffPoint().get()).get(
								i));
					}
				}
			}

			Logger.logInfo("cutoffs at " + cutoffPoints.keySet().stream().map(Pair::toString).collect(Collectors
				.joining(", ")));
		}
	}

	private Optional<Pair<Integer, Integer>> belowCutoffPoint() {
		return cutoffPoints.keySet().stream().filter(_cutoffPoint -> maxNumValue >= _cutoffPoint.getKey()
			&& maxLenValue >= _cutoffPoint.getValue()).findFirst();
	}

	private List<String> evaluate(String systemName, int maxNumValue, int maxLenValue, int i) {
		writer.createNewLine();
		writer.addValue(systemIndex);
		writer.addValue(maxNumValue);
		writer.addValue(maxLenValue);
		writer.addValue(i);

		Result<List<String>> result = new Result<>();
		processRunner.run(new TseytinAlgorithm(config.modelPath, systemName, maxNumValue,
			maxLenValue, i, config.tempPath, "model2cnf", config.timeout.getValue()), result);
		List<String> resultList = cleanResults(result.getResult(), 5);
		processRunner.run(new TseytinAlgorithm(config.modelPath, systemName, maxNumValue,
			maxLenValue, i, config.tempPath, "sat", config.timeout.getValue()), result);
		resultList.addAll(cleanResults(result.getResult(), 2));
//		processRunner.run(new TseytinAlgorithm(config.modelPath, systemName, maxNumValue,
//				maxLenValue, i, config.tempPath, "core-dead", config.timeout.getValue()), result);
//		resultList.addAll(cleanResults(result.getResult(), 1));
		resultList.forEach(writer::addValue);
		writer.addValue(false);
		writer.flush();
		return resultList;
	}

	private void skipEvaluate(int maxNumValue, int maxLenValue, int i, List<String> resultList) {
		if (resultList == null)
			resultList = cleanResults(new ArrayList<>(), 7);
		if (config.debug.getValue() > 0) {
			writer.createNewLine();
			writer.addValue(systemIndex);
			writer.addValue(maxNumValue);
			writer.addValue(maxLenValue);
			writer.addValue(i);
			resultList.forEach(writer::addValue);
			writer.addValue(true);
			writer.flush();
		}
	}

	List<String> cleanResults(List<String> resultList, int expectedNum) {
		List<String> results = new ArrayList<>();
		results = resultList.stream()
			.filter(line -> line.startsWith("res="))
			.map(line -> line.replace("res=", ""))
			.collect(Collectors.toList());
		if (results.size() != expectedNum) {
			for (int j = 0; j < expectedNum; j++)
				results.add("NA");
		}
		return results;
	}
}
