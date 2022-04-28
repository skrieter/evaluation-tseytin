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

import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

import de.ovgu.featureide.fm.core.analysis.cnf.*;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.*;
import de.ovgu.featureide.fm.core.base.*;
import de.ovgu.featureide.fm.core.job.monitor.*;

public class CoreDeadFeatureIDE extends Analysis.FeatureIDEAnalysis {
	private static final long serialVersionUID = 382302837042742104L;

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
			.convertToString(coreDead, true, true, true);
		coreDeadFeatures.sort(Collator.getInstance());
		Files.write(getTempPath("coredeadf"), String.join("\n", coreDeadFeatures).getBytes());
		printResult(new Result<>(result.timeNeeded, coreDead.size(), md5(coreDeadFeatures)));
	}
}
