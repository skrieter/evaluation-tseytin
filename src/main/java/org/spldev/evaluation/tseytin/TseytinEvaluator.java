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
import org.spldev.formula.expression.io.parse.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.util.io.csv.*;
import org.spldev.util.io.format.*;
import org.spldev.util.logging.*;

/**
 * Evaluate the (hybrid) Tseytin transformation. This assumes that input
 * formulas are in a "partial" CNF (i.e., already a conjunction of some
 * formulas).
 */
public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer, systemWriter;
	protected static final ListProperty<Integer> maxNumValues = new ListProperty<>("maxNum", Property.IntegerConverter);
	protected static final ListProperty<Integer> maxLenValues = new ListProperty<>("maxLen", Property.IntegerConverter);

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
				"SatTime", "Sat"));
		systemWriter = addCSVWriter("systems.csv", Arrays.asList("ID", "System", "Features", "Constraints"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final int systemIndexEnd = config.systemNames.size();
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(config.modelPath);
		fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
		final ProcessRunner processRunner = new ProcessRunner();
		processRunner.setTimeout(config.timeout.getValue());
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			final String systemName = config.systemNames.get(systemIndex);
			logSystem();
			tabFormatter.incTabLevel();
			final Formula formula = fmReader.read(systemName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			systemWriter.createNewLine();
			systemWriter.addValue(systemIndex);
			systemWriter.addValue(systemName);
			systemWriter.addValue(VariableMap.fromExpression(formula).size());
			systemWriter.addValue(NormalForms.simplifyForNF(formula).getChildren().size());
			systemWriter.flush();
			for (final int maxNumValue : maxNumValues.getValue()) {
				for (final int maxLenValue : maxLenValues.getValue()) {
					for (int i = 0; i < config.systemIterations.getValue(); i++) {
						try {
							Logger.logInfo("Running for " + systemName + " " + maxNumValue + " " + maxLenValue);
							writer.createNewLine();
							writer.addValue(systemIndex);
							writer.addValue(maxNumValue);
							writer.addValue(maxLenValue);
							writer.addValue(i);

							Result<List<String>> result = new Result<>();
							processRunner.run(new TseytinAlgorithm(config.modelPath, systemName, maxNumValue,
								maxLenValue, config.tempPath, "model2cnf"), result);
							writeResults(result.getResult(), 5);

							result = new Result<>();
							processRunner.run(new TseytinAlgorithm(config.modelPath, systemName, maxNumValue,
								maxLenValue, config.tempPath, "sat"), result);
							writeResults(result.getResult(), 2);
						} finally {
							writer.flush();
						}
					}
				}
			}
			tabFormatter.decTabLevel();
		}
	}

	void writeResults(List<String> resultList, int expectedNum) {
		resultList = resultList.stream()
			.filter(line -> line.startsWith("res="))
			.map(line -> line.replace("res=", ""))
			.collect(Collectors.toList());
		if (resultList.size() == 0) {
			for (int j = 0; j < expectedNum; j++) {
				writer.addValue("NA");
			}
		} else {
			resultList.forEach(writer::addValue);
		}
	}
}
