package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormatCNF;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.transform.CNFTransformer;
import org.spldev.formula.solver.javasmt.CNFTseytinTransformer;

import java.nio.file.Paths;

public class Transform extends Analysis {
	@Override
	public void run() throws Exception {
		parameters.transformation.run();
	}

	public static class TseytinZ3 extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			processFormulaResult(executeTransformer(formula, new CNFTseytinTransformer()));
		}
	}

	public static class TseytinSPLDev extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			CNFTransformer transformer = new CNFTransformer();
			transformer.setMaximumNumberOfClauses(0);
			transformer.setMaximumLengthOfClauses(0);
			transformer.setMaximumNumberOfLiterals(0);
			processFormulaResult(executeTransformer(formula, transformer));
		}
	}

	public static class DistribFeatureIDE extends Transformation {
		@Override
		public void run() {
			final IFeatureModel featureModel = FeatureModelManager
				.load(Paths.get(parameters.rootPath).resolve(parameters.modelPath));
			if (featureModel != null) {
				Result<CNF> result = execute(() -> {
					final long localTime = System.nanoTime();
					CNF cnf = new FeatureModelFormula(featureModel).getCNF();
					final long timeNeeded = System.nanoTime() - localTime;
					return new Result<>(timeNeeded, null, cnf);
				});
				if (result != null) {
					printResult(result.timeNeeded);
					printResult(result.result.getVariables().size());
					printResult(result.result.getClauses().size());
					de.ovgu.featureide.fm.core.io.manager.FileHandler.save(getTempPath(), result.result,
						new DIMACSFormatCNF());
				}
			}
		}
	}

	public static class DistribSPLDev extends Transformation {
		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			CNFTransformer transformer = new CNFTransformer();
			transformer.setMaximumNumberOfClauses(Integer.MAX_VALUE);
			transformer.setMaximumLengthOfClauses(Integer.MAX_VALUE);
			transformer.setMaximumNumberOfLiterals(Integer.MAX_VALUE);
			processFormulaResult(executeTransformer(formula, transformer));
		}
	}
}
