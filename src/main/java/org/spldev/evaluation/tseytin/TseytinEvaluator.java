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

import org.spldev.evaluation.*;
import org.spldev.evaluation.util.*;
import org.spldev.formula.*;
import org.spldev.formula.analysis.sat4j.*;
import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.parse.*;
import org.spldev.util.*;
import org.spldev.util.extension.*;
import org.spldev.util.io.csv.*;
import org.spldev.util.io.format.*;
import org.spldev.util.logging.*;

public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer;

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
		writer = addCSVWriter("evaluation.csv", Arrays.asList("System", "Mode", "Iteration", "Transform Time",
			"Analysis Time", "Variables", "Clauses", "TseytinTransformed"));

		// TODO Number of tseytin transformed
		// TODO Matrix: clauses to metrics
	}

	@Override
	public void evaluate() {
		tabFormatter.setTabLevel(0);
		final int systemIndexEnd = config.systemNames.size();
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			logSystem();
			tabFormatter.incTabLevel();
			final String systemName = config.systemNames.get(systemIndex);
			final ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(config.modelPath);
//			fmReader.setFormatSupplier(FormatSupplier.of(new XmlFeatureModelFormat()));
			fmReader.setFormatSupplier(FormatSupplier.of(new KConfigReaderFormat()));
			final Formula formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException(
				"no feature model"));
			for (int i = 0; i < config.systemIterations.getValue(); i++) {
//				Logger.logInfo("Distributive Transform");
//				transformToCNF(systemName, formula, i, CCNFProvider.fromFormula(), CNFProvider.fromFormula(),
//					"distrib");
				Logger.logInfo("Tseytin Transform");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(0), CNFProvider
					.fromTseytinFormula(), "tseytin");
				Logger.logInfo("Hybrid Tseytin Transform");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(1), CNFProvider
					.fromTseytinFormula(), "hybrid1");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(10), CNFProvider
					.fromTseytinFormula(), "hybrid10");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(100), CNFProvider
					.fromTseytinFormula(), "hybrid100");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(1_000), CNFProvider
					.fromTseytinFormula(), "hybrid1_000");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(10_000), CNFProvider
					.fromTseytinFormula(), "hybrid10_000");
				transformToCNF(systemName, formula, i, FormulaProvider.TseytinCNF.fromFormula(100_000), CNFProvider
					.fromTseytinFormula(), "hybrid100_000");
			}
			tabFormatter.decTabLevel();
		}
	}

	private void transformToCNF(String systemName, Formula formula, int i, Provider<Formula> transformer,
		Provider<CNF> cnfProvider, String transformerName) {
		writer.createNewLine();
		try {
			writer.addValue(systemName);
			writer.addValue(transformerName);
			writer.addValue(i);

			long localTime, timeNeeded;
			final ModelRepresentation rep = new ModelRepresentation(formula);

			localTime = System.nanoTime();
			formula = rep.get(transformer);
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(timeNeeded);

			localTime = System.nanoTime();
			final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
			hasSolutionAnalysis.setSolverInputProvider(cnfProvider);
			hasSolutionAnalysis.getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(timeNeeded);

			writer.addValue(VariableMap.fromExpression(formula).size());
			writer.addValue(formula.getChildren().size());
		} finally {
			writer.flush();
		}
	}

}
