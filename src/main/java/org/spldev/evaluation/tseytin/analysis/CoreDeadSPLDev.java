package org.spldev.evaluation.tseytin.analysis;

import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.CoreDeadAnalysis;
import org.spldev.formula.clauses.LiteralList;

import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CoreDeadSPLDev extends Analysis.SPLDevAnalysis {
	@Override
	public void run(ModelRepresentation rep) {
		printResult(execute(() -> {
			final long localTime = System.nanoTime();
			LiteralList coreDead = new CoreDeadAnalysis().getResult(rep).orElseThrow();
			final long timeNeeded = System.nanoTime() - localTime;
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
			return new Result<>(timeNeeded, md5(coreDeadFeatures), coreDead.size());
		}));
	}
}
