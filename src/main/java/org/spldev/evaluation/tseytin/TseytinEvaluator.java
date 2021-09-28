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
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.clauses.CNF;
import org.spldev.formula.clauses.CNFProvider;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.FormulaProvider;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.parse.KConfigReaderFormat;
import org.spldev.formula.expression.transform.CNFTseytinTransformer;
import org.spldev.util.Provider;
import org.spldev.util.data.Pair;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Evaluate the (hybrid) Tseitin transformation. This assumes that input
 * formulas are in a "partial" CNF (i.e., already a conjunction of some
 * formulas).
 */
public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer;

	int[] maxNumValues = { 1, 10, 100, 1000, 10000 };
	int[] maxLenValues = { 1, 10, 100, 1000, 10000 };

	private static class ResultLine {
		String system;
		long transformTime, analysisTime;
		int maxNumOfClauses, maxLenOfClauses, variables, clauses, tseytinClauses;

		static ResultLine reduce(ArrayList<ResultLine> resultLines) {
			ResultLine resultLine = new ResultLine();
			OptionalDouble averageTransformTime = resultLines.stream().map(_resultLine -> _resultLine.transformTime)
				.mapToLong(Long::longValue).average();
			OptionalDouble averageAnalysisTime = resultLines.stream().map(_resultLine -> _resultLine.analysisTime)
				.mapToLong(Long::longValue).average();
			resultLine.system = resultLines.get(0).system;
			resultLine.transformTime = (long) averageTransformTime.orElse(0);
			resultLine.analysisTime = (long) averageAnalysisTime.orElse(0);
			resultLine.maxNumOfClauses = resultLines.get(0).maxNumOfClauses;
			resultLine.maxLenOfClauses = resultLines.get(0).maxLenOfClauses;
			resultLine.variables = resultLines.get(0).variables;
			resultLine.clauses = resultLines.get(0).clauses;
			resultLine.tseytinClauses = resultLines.get(0).tseytinClauses;
			return resultLine;
		}

		private void print(CSVWriter writer) {
			writer.createNewLine();
			writer.addValue(system);
			writer.addValue(maxNumOfClauses);
			writer.addValue(maxLenOfClauses);
			writer.addValue(transformTime);
			writer.addValue(analysisTime);
			writer.addValue(variables);
			writer.addValue(clauses);
			writer.addValue(tseytinClauses);
			writer.flush();
		}
	}

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
		writer = addCSVWriter("evaluation.csv", Arrays.asList("System", "MaxNumOfClauses", "MaxLenOfClauses",
			"TransformTime", "AnalysisTime", "Variables", "Clauses", "TseytinClauses"));
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final int systemIndexEnd = config.systemNames.size();
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			logSystem();
			tabFormatter.incTabLevel();
			final String systemName = config.systemNames.get(systemIndex);
			HashMap<Pair<Integer, Integer>, ResultLine> resultMatrix = new HashMap<>();
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(config.modelPath);
			fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
			final Formula formula = fmReader.read(systemName)
				.orElseThrow(p -> new RuntimeException("no feature model"));
			warmUpRun(formula, FormulaProvider.TseytinCNF.fromFormula(), CNFProvider.fromTseytinFormula());
			for (int maxNumValue : maxNumValues) {
				for (int maxLenValue : maxLenValues) {
					ArrayList<ResultLine> resultLines = new ArrayList<>();
					for (int i = 0; i < config.systemIterations.getValue(); i++) {
						resultLines.add(transformToCNF(systemName, formula,
							FormulaProvider.TseytinCNF.fromFormula(maxNumValue, maxLenValue),
							CNFProvider.fromTseytinFormula(), maxNumValue, maxLenValue));
					}
					ResultLine resultLine = ResultLine.reduce(resultLines);
					resultLine.print(writer);
					resultMatrix.put(new Pair<>(maxNumValue, maxLenValue), resultLine);
				}
			}
			tabFormatter.decTabLevel();
			writeMatrix(resultMatrix, systemName, "TransformTime", resultLine -> resultLine.transformTime);
			writeMatrix(resultMatrix, systemName, "AnalysisTime", resultLine -> resultLine.analysisTime);
			writeMatrix(resultMatrix, systemName, "Variables", resultLine -> (long) resultLine.variables);
			writeMatrix(resultMatrix, systemName, "Clauses", resultLine -> (long) resultLine.clauses);
			writeMatrix(resultMatrix, systemName, "TseytinClauses", resultLine -> (long) resultLine.tseytinClauses);
		}
	}

	private ResultLine transformToCNF(String systemName, Formula formula, Provider<Formula> transformer,
		Provider<CNF> cnfProvider, int maximumNumberOfClauses, int maximumLengthOfClauses) {
		System.out.printf("Running for %s and maxNum=%d, maxLen=%d\n", systemName, maximumNumberOfClauses,
			maximumLengthOfClauses);
		ResultLine resultLine = new ResultLine();
		resultLine.system = systemName;
		resultLine.maxNumOfClauses = maximumNumberOfClauses;
		resultLine.maxLenOfClauses = maximumLengthOfClauses;

		long localTime, timeNeeded;
		final ModelRepresentation rep = new ModelRepresentation(formula);

		localTime = System.nanoTime();
		formula = rep.get(transformer);
		timeNeeded = System.nanoTime() - localTime;
		resultLine.transformTime = timeNeeded;

		localTime = System.nanoTime();
		final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
		hasSolutionAnalysis.setSolverInputProvider(cnfProvider);
		hasSolutionAnalysis.getResult(rep).get();
		timeNeeded = System.nanoTime() - localTime;
		resultLine.analysisTime = timeNeeded;

		resultLine.variables = VariableMap.fromExpression(formula).size();
		resultLine.clauses = formula.getChildren().size();
		resultLine.tseytinClauses = CNFTseytinTransformer.getNumberOfTseytinTransformedClauses();
		return resultLine;
	}

	private void warmUpRun(Formula formula, Provider<Formula> transformer, Provider<CNF> cnfProvider) {
		ModelRepresentation modelRepresentation = new ModelRepresentation(formula);
		modelRepresentation.get(transformer);
		final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
		hasSolutionAnalysis.setSolverInputProvider(cnfProvider);
		hasSolutionAnalysis.getResult(modelRepresentation).get();
	}

	private void writeMatrix(HashMap<Pair<Integer, Integer>, ResultLine> resultMatrix,
		String systemName, String metric, Function<ResultLine, Long> metricFn) {
		final CSVWriter csvWriter = new CSVWriter();
		try {
			csvWriter.setOutputDirectory(config.csvPath);
			csvWriter.setFileName(metric + "/" + systemName.replace("/", "_").replace(".kconfigreader.model", "")
				+ ".csv");
			for (int maxNumValue : maxNumValues) {
				csvWriter.createNewLine();
				for (int maxLenValue : maxLenValues) {
					csvWriter.addValue(metricFn.apply(resultMatrix.get(new Pair<>(maxNumValue, maxLenValue))));
				}
				csvWriter.flush();
			}
		} catch (final IOException e) {
			Logger.logError(e);
		}
	}
}
