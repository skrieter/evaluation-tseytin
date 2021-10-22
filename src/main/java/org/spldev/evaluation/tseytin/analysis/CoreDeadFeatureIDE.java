package org.spldev.evaluation.tseytin.analysis;

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;
import org.spldev.formula.clauses.LiteralList;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.List;
import java.util.stream.Collectors;

public class CoreDeadFeatureIDE extends Analysis.FeatureIDEAnalysis {
	@Override
	public void run(IFeatureModel featureModel) throws IOException {
		CNF cnf = new FeatureModelFormula(featureModel).getCNF();
		Result<LiteralSet> result = execute(() -> new de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis(
			cnf)
				.analyze(new NullMonitor<>()));
		if (result == null)
			return;
		LiteralSet coreDead = result.payload;
		coreDead = coreDead.retainAll(cnf.getVariables().convertToLiterals(
			getActualFeatures(cnf), true, true));
		List<String> coreDeadFeatures = cnf.getVariables()
			.convertToString(coreDead, true, true, true)
			.stream().map(feature -> feature.replace("|", "")).collect(Collectors.toList());
		coreDeadFeatures.sort(Collator.getInstance());
		Files.write(getTempPath("coredeadf"), String.join("\n", coreDeadFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, coreDead.size(), md5(coreDeadFeatures)));
	}
}
