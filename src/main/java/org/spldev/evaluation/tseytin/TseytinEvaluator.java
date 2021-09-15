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

	public static void main(String[] args) {
		ExtensionLoader.load();
		new TseytinEvaluator().run(Arrays.asList("config"));
	}

	@Override
	public String getId() {
		return "eval-tseytin";
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
