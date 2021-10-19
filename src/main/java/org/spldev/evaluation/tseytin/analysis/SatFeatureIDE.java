package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

public class SatFeatureIDE extends Analysis.FeatureIDEAnalysis {
	@Override
	public void run(IFeatureModel featureModel) throws Exception {
		CNF cnf = new FeatureModelFormula(featureModel).getCNF(); // todo: can this be done faster?
		printResult(execute(() -> {
			final long localTime = System.nanoTime();
			Boolean sat = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.HasSolutionAnalysis(cnf)
				.analyze(new NullMonitor<>());
			final long timeNeeded = System.nanoTime() - localTime;
			return new Result<>(timeNeeded, null, sat);
		}));
	}
}
