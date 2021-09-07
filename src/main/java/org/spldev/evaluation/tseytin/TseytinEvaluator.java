package org.spldev.evaluation.tseytin;

import java.util.Arrays;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.expression.CCNFProvider;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.TsyetinCNFProvider;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.io.parse.KMaxFormat;
import org.spldev.util.Provider;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.logging.Logger;

public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer;

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
		int systemIndexEnd = config.systemNames.size();
		for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
			logSystem();
			tabFormatter.incTabLevel();
			String systemName = config.systemNames.get(systemIndex);
			ModelReader<Formula> fmReader = new ModelReader<>();
			fmReader.setPathToFiles(config.modelPath);
//			fmReader.setFormatSupplier(FormatSupplier.of(new XmlFeatureModelFormat()));
			fmReader.setFormatSupplier(FormatSupplier.of(new KMaxFormat()));
			Formula formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException("no feature model"));
			for (int i = 0; i < config.systemIterations.getValue(); i++) {
				Logger.logInfo("Distributive Transform");
				transformToCNF(systemName, formula, i, CCNFProvider.fromFormula(), "distrib");
				Logger.logInfo("Tsyetin Transform");
				transformToCNF(systemName, formula, i, TsyetinCNFProvider.fromFormula(), "tseytin");
			}
			tabFormatter.decTabLevel();
		}
	}

	private void transformToCNF(String systemName, Formula formula, int i, Provider<Formula> transformer, String transformerName) {
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
			new HasSolutionAnalysis().getResult(rep).get();
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			writer.addValue(VariableMap.fromExpression(formula).size());
			writer.addValue(formula.getChildren().size());
		} finally {
			writer.flush();
		}
	}

}
