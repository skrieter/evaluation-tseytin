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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import org.spldev.evaluation.*;
import org.spldev.evaluation.process.*;
import org.spldev.evaluation.tseytin.analysis.Analysis;
import org.spldev.evaluation.util.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.util.data.Pair;
import org.spldev.util.io.csv.*;

public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer, systemWriter;
	protected ProcessRunner processRunner;

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
		List<String> resultColumns = new ArrayList<>();
		resultColumns.add("ID");
		resultColumns.add("Iteration");
		resultColumns.add("Transformation");
		Analysis.analyses.forEach(analysisPair -> resultColumns.addAll(Arrays.asList(
			analysisPair.getValue())));
		writer = addCSVWriter("evaluation.csv", resultColumns);
		systemWriter = addCSVWriter("systems.csv", Arrays.asList("ID", "System", "Features", "Constraints"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(config.modelPath);
		fmReader.setFormatSupplier(FormulaFormatManager.getInstance());
		processRunner = new ProcessRunner();
		processRunner.setTimeout(config.timeout.getValue() * 2);
		for (systemIteration = 0; systemIteration < config.systemIterations.getValue(); systemIteration++) {
			for (systemIndex = 0; systemIndex < config.systemNames.size(); systemIndex++) {
				String modelPath = config.systemNames.get(systemIndex);
				String system = modelPath
					.replace(".kconfigreader.model", "")
					.replace(".xml", "");
				if (systemIteration == 0) {
					Formula formula = fmReader.read(modelPath).orElseThrow(p -> new RuntimeException(
						"no feature model"));
					writeCSV(systemWriter, systemWriter -> {
						systemWriter.addValue(systemIndex);
						systemWriter.addValue(system);
						systemWriter.addValue(VariableMap.fromExpression(formula).size());
						systemWriter.addValue(NormalForms.simplifyForNF(formula).getChildren().size());
					});
				}
				Parameters parameters = new Parameters(
					system, config.modelPath.toString(),
					modelPath, systemIteration, config.tempPath.toString(), config.timeout.getValue());
				tabFormatter.setTabLevel(0);
				logSystem();
				tabFormatter.setTabLevel(1);
				Arrays.stream(Analysis.transformations).forEach(transformation -> {
					List<String> results = evaluateForParameters(parameters, transformation);
					writeCSV(writer, writer -> {
						writer.addValue(systemIndex);
						writer.addValue(systemIteration);
						writer.addValue(transformation.toString());
						results.forEach(writer::addValue);
					});
				});
			}
		}
	}

	private List<String> evaluateForParameters(Parameters parameters, Analysis transformation) {
		tabFormatter.setTabLevel(2);
		List<String> results = new ArrayList<>();
		Analysis.analyses.stream().map(Pair::getKey).forEach(analysisClass -> {
			try {
				Analysis analysis = (Analysis) analysisClass.getConstructor().newInstance();
				results.addAll(run(parameters, analysis, transformation));
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException e) {
				e.printStackTrace();
			}
		});
		return results;
	}

	private List<String> run(Parameters parameters, Analysis analysis, Analysis transformation) {
		analysis.setParameters(parameters);
		transformation.setParameters(parameters);
		Wrapper wrapper = new Wrapper(analysis);
		wrapper.setTransformation(transformation);
		tabFormatter.incTabLevel();
		List<String> results = processRunner.run(wrapper).getResult();
		while (results.size() < wrapper.analysis.getResultColumns().length)
			results.add("NA");
		tabFormatter.decTabLevel();
		return results;
	}
}
