package org.spldev.evaluation.tseytin;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;
import org.spldev.formula.clauses.CNF;
import org.spldev.formula.clauses.Clauses;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.Formulas;
import org.spldev.formula.expression.io.XmlFeatureModelFormat;
import org.spldev.formula.expression.transform.NFTransformer;
import org.spldev.formula.expression.transform.TseytinTransformer;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.tree.Trees;

import java.util.Arrays;
import java.util.function.Function;

public class TseytinEvaluator extends Evaluator {
	protected CSVWriter writer;

	@Override
	public String getId() {
		return "eval-tseytin";
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		writer = addCSVWriter("evaluation.csv",
				Arrays.asList("System", "Mode", "Iteration", "Simplification Time", "Transform Time", "Convert Time", "Analysis Time", "Variables", "Clauses"));
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
			fmReader.setFormatSupplier(FormatSupplier.of(new XmlFeatureModelFormat()));
			for (int i = 0; i < config.systemIterations.getValue(); i++) { // jvm warmup? better call the transformation in this process or start another process? (memory leaks?)
				Formula formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException("no feature model"));
				transformToCNF(systemName, formula, i, TseytinEvaluator::toDistribCNF, "distrib"); // clausal CNF?
				formula = fmReader.read(systemName).orElseThrow(p -> new RuntimeException("no feature model")); // optimize this?
				transformToCNF(systemName, formula, i, TseytinEvaluator::toTseytinCNF, "tseytin");
			}
			tabFormatter.decTabLevel();
		}
	}

	private void transformToCNF(String systemName, Formula formula, int i, Function<Formula, Formula> transformer, String transformerName) {
		writer.createNewLine();
		try {
			writer.addValue(systemName);
			writer.addValue(transformerName);
			writer.addValue(i);

			long localTime = System.nanoTime();
			formula = NFTransformer.simplifyForNF(formula);
			long timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0)); // formats sometimes in scientific notation?

			localTime = System.nanoTime();
			formula = transformer.apply(formula);
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			localTime = System.nanoTime();
			CNF cnf = Clauses.convertToCNF(formula);
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			localTime = System.nanoTime();
			new HasSolutionAnalysis().getResult(new ModelRepresentation(formula)).get();
			timeNeeded = System.nanoTime() - localTime;
			writer.addValue(String.format("%.1f", timeNeeded / 1000_000.0));

			writer.addValue(cnf.getVariables().size());
			writer.addValue(cnf.getClauses().size());
//			FileHandler.save(cnf,
//					config.csvPath.resolve(systemName + "-" + i + "-" + transformerName + ".dimacs"),
//					new DIMACSFormatCNF());
//		} catch (final IOException e) {
//			writer.removeLastLine();
//			Logger.logError(e);
		} finally {
			writer.flush();
		}
	}

	private static Formula toDistribCNF(Formula formula) {
		return NFTransformer.distributiveLawTransform(formula, Formulas.NormalForm.CNF);
	}

	public static Formula toTseytinCNF(Formula formula) {
		return Trees.traverse(formula, new TseytinTransformer()).get(); // this has side effects on the formula? why not TreeVisitor<Expression>?
	}
}
