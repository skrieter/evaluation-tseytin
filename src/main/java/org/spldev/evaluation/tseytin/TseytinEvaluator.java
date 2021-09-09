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
			"Analysis Time", "Variables", "Clauses"));
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
			fmReader.setFormatSupplier(FormatSupplier.of(new KMaxFormat()));
			final Formula formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException(
				"no feature model"));
			for (int i = 0; i < config.systemIterations.getValue(); i++) {
				Logger.logInfo("Distributive Transform");
				transformToCNF(systemName, formula, i, CCNFProvider.fromFormula(), CNFProvider.fromFormula(),
					"distrib");
				Logger.logInfo("Tsyetin Transform");
				transformToCNF(systemName, formula, i, TseytinCNFProvider.fromFormula(), CNFProvider
					.fromTseytinFormula(), "tseytin");
				Logger.logInfo("Hybrid Tsyetin Transform");
				transformToCNF(systemName, formula, i, TseytinCNFProvider.fromFormula(), CNFProvider
					.fromTseytinFormula(), "hybrid");
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
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			localTime = System.nanoTime();
			final HasSolutionAnalysis hasSolutionAnalysis = new HasSolutionAnalysis();
			hasSolutionAnalysis.setSolverInputProvider(cnfProvider);
			hasSolutionAnalysis.getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			writer.addValue(VariableMap.fromExpression(formula).size());
			writer.addValue(formula.getChildren().size());
		} finally {
			writer.flush();
		}
	}

}
