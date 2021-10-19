package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

import java.util.List;

public class CoreDeadFeatureIDE extends Analysis.FeatureIDEAnalysis {
	@Override
	public void run(IFeatureModel featureModel) {
		CNF cnf = new FeatureModelFormula(featureModel).getCNF();
		printResult(execute(() -> {
			final long localTime = System.nanoTime();
			LiteralSet coreDead = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis(cnf)
				.analyze(new NullMonitor<>());
			final long timeNeeded = System.nanoTime() - localTime;
			if (coreDead == null)
				return null;
			coreDead = coreDead.retainAll(cnf.getVariables().convertToLiterals(
				getActualFeatures(cnf), true, true));
			List<String> coreDeadFeatures = cnf.getVariables()
				.convertToString(coreDead, true, true, true);
			return new Result<>(timeNeeded, md5(coreDeadFeatures), coreDead.size());
		}));
	}
}
