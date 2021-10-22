package org.spldev.evaluation.tseytin.analysis;

import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.CoreDeadAnalysis;
import org.spldev.formula.clauses.LiteralList;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CoreDeadSPLDev extends Analysis.SPLDevAnalysis {
	@Override
	public void run(ModelRepresentation rep) throws IOException {
		Result<LiteralList> result = execute(() -> new CoreDeadAnalysis().getResult(rep).get());
		if (result == null)
			return;
		LiteralList coreDead = result.payload;
		coreDead = coreDead.retainAll(LiteralList.getLiterals(rep.getVariables(), getActualFeatures(rep)));
		List<String> coreDeadFeatures = Arrays.stream(coreDead.getPositiveLiterals().getVariables()
			.getLiterals())
			.mapToObj(index -> rep.getVariables().getName(index)).filter(Optional::isPresent).map(Optional::get)
			.collect(Collectors.toList());
		coreDeadFeatures.addAll(Arrays.stream(coreDead.getNegativeLiterals().getVariables().getLiterals())
			.mapToObj(index -> rep.getVariables().getName(index)).filter(Optional::isPresent).map(Optional::get)
			.map(name -> "-" + name)
			.collect(Collectors.toList()));
		coreDeadFeatures.sort(Collator.getInstance());
		Files.write(getTempPath("coredeads"), String.join("\n", coreDeadFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, coreDead.size(), md5(coreDeadFeatures)));
	}
}
