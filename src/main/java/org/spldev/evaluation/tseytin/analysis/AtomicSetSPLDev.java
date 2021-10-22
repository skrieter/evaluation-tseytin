package org.spldev.evaluation.tseytin.analysis;

import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.AtomicSetAnalysis;
import org.spldev.formula.clauses.LiteralList;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AtomicSetSPLDev extends Analysis.SPLDevAnalysis {
	@Override
	public void run(ModelRepresentation rep) throws IOException {
		Result<List<LiteralList>> result = execute(() -> new AtomicSetAnalysis().getResult(rep).get());
		if (result == null)
			return;
		List<LiteralList> atomicSets = result.payload;
		LiteralList actualFeatures = LiteralList.getLiterals(rep.getVariables(), getActualFeatures(rep));
		atomicSets = atomicSets.stream()
			.map(atomicSet -> atomicSet.retainAll(actualFeatures))
			.filter(atomicSet -> !atomicSet.isEmpty())
			.collect(Collectors.toList());
		List<List<String>> atomicSetFeatures = atomicSets.stream().map(atomicSet -> Arrays.stream(atomicSet
			.getVariables().getLiterals())
			.mapToObj(index -> rep.getVariables().getName(index)).filter(Optional::isPresent).map(Optional::get)
			.map(feature -> feature.replace("|", ""))
			.collect(Collectors.toList())).collect(Collectors.toList());
		atomicSetFeatures.forEach(atomicSet -> atomicSet.sort(Collator.getInstance()));
		List<String> flatAtomicSetFeatures = atomicSetFeatures.stream().map(Object::toString).sorted(Collator
			.getInstance()).collect(Collectors
				.toList());
		Files.write(getTempPath("atomicsetss"), String.join("\n", flatAtomicSetFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, atomicSets.size(), md5(flatAtomicSetFeatures)));

	}
}
