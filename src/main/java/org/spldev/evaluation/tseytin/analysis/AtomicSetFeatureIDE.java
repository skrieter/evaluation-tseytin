package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AtomicSetAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.List;
import java.util.stream.Collectors;

public class AtomicSetFeatureIDE extends Analysis.FeatureIDEAnalysis {
	@Override
	public void run(IFeatureModel featureModel) throws IOException {
		CNF cnf = new FeatureModelFormula(featureModel).getCNF();
		Result<List<LiteralSet>> result = execute(() -> new AtomicSetAnalysis(cnf).analyze(new NullMonitor<>()));
		if (result == null)
			return;
		List<LiteralSet> atomicSets = result.payload;
		LiteralSet actualFeatures = cnf.getVariables().convertToLiterals(getActualFeatures(cnf),
			true, true);
		atomicSets = atomicSets.stream()
			.map(atomicSet -> atomicSet.retainAll(actualFeatures))
			.filter(atomicSet -> !atomicSet.isEmpty())
			.collect(Collectors.toList());
		List<List<String>> atomicSetFeatures = atomicSets.stream().map(atomicSet -> cnf.getVariables()
			.convertToString(atomicSet.getVariables(), true, false, false).stream()
			.map(feature -> feature.replace("|", "")).collect(Collectors.toList()))
			.collect(Collectors.toList());
		atomicSetFeatures.forEach(atomicSet -> atomicSet.sort(Collator.getInstance()));
		List<String> flatAtomicSetFeatures = atomicSetFeatures.stream().map(Object::toString).sorted(Collator
			.getInstance()).collect(Collectors
				.toList());
		Files.write(getTempPath("atomicsetsf"), String.join("\n", flatAtomicSetFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, atomicSets.size(), md5(flatAtomicSetFeatures)));
	}
}
