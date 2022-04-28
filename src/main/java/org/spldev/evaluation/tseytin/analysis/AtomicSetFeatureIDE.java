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

import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.AtomicSetAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AtomicSetFeatureIDE extends Analysis.FeatureIDEAnalysis {
	private static final long serialVersionUID = -5961485770212050719L;

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
		List<List<String>> atomicSetFeatures = atomicSets.stream().map(atomicSet -> new ArrayList<>(cnf.getVariables()
			.convertToString(atomicSet.getVariables(), true, false, false)))
			.collect(Collectors.toList());
		atomicSetFeatures.forEach(atomicSet -> atomicSet.sort(Collator.getInstance()));
		List<String> flatAtomicSetFeatures = atomicSetFeatures.stream().map(Object::toString).sorted(Collator
			.getInstance()).collect(Collectors
				.toList());
		Files.write(getTempPath("atomicsetsf"), String.join("\n", flatAtomicSetFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, atomicSets.size(), md5(flatAtomicSetFeatures)));
	}
}
