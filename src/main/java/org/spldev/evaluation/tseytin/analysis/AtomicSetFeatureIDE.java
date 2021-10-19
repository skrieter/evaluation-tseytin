package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

import java.text.Collator;
import java.util.List;
import java.util.stream.Collectors;

public class AtomicSetFeatureIDE extends Analysis.FeatureIDEAnalysis {
	@Override
	public void run(IFeatureModel featureModel) {
		CNF cnf = new FeatureModelFormula(featureModel).getCNF();
		printResult(execute(() -> {
			final long localTime = System.nanoTime();
			List<LiteralSet> atomicSets = new de.ovgu.featureide.fm.core.analysis.cnf.analysis.AtomicSetAnalysis(
				cnf).analyze(new NullMonitor<>());
			final long timeNeeded = System.nanoTime() - localTime;
			if (atomicSets == null)
				return null;
			LiteralSet actualFeatures = cnf.getVariables().convertToLiterals(getActualFeatures(cnf),
				true, true);
			atomicSets = atomicSets.stream()
				.map(atomicSet -> atomicSet.retainAll(actualFeatures))
				.filter(atomicSet -> !atomicSet.isEmpty())
				.collect(Collectors.toList());
			List<List<String>> atomicSetFeatures = atomicSets.stream().map(atomicSet -> cnf.getVariables()
				.convertToString(atomicSet.getVariables(), true, false, false))
				.collect(Collectors.toList());
			atomicSetFeatures.forEach(atomicSet -> atomicSet.sort(Collator.getInstance()));
			List<String> flatAtomicSetFeatures = atomicSetFeatures.stream().map(Object::toString).collect(Collectors
				.toList());
			return new Result<>(timeNeeded, md5(flatAtomicSetFeatures), atomicSets.size());
		}));
	}
}
