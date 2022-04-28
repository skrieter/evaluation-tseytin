/* -----------------------------------------------------------------------------
 * Evaluation-Tseytin - Program for the evaluation of the Tseytin transformation.
 * Copyright (C) 2021  Sebastian Krieter, Elias Kuiter
 * 
 * This file is part of Evaluation-Tseytin.
 * 
 * Evaluation-Tseytin is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-Tseytin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Tseytin.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/ekuiter/evaluation-tseytin> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin.analysis;

import org.spldev.analysis.sat4j.*;
import org.spldev.clauses.*;
import org.spldev.formula.ModelRepresentation;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AtomicSetSPLDev extends Analysis.SPLDevAnalysis {
	private static final long serialVersionUID = 304612797727601028L;

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
			.collect(Collectors.toList())).collect(Collectors.toList());
		atomicSetFeatures.forEach(atomicSet -> atomicSet.sort(Collator.getInstance()));
		List<String> flatAtomicSetFeatures = atomicSetFeatures.stream().map(Object::toString).sorted(Collator
			.getInstance()).collect(Collectors
				.toList());
		Files.write(getTempPath("atomicsetss"), String.join("\n", flatAtomicSetFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, atomicSets.size(), md5(flatAtomicSetFeatures)));

	}
}
