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

import java.nio.file.*;

import org.spldev.analysis.javasmt.solver.*;
import org.spldev.formula.structure.*;
import org.spldev.formula.structure.transform.*;

import de.ovgu.featureide.fm.core.analysis.cnf.*;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.*;
import de.ovgu.featureide.fm.core.base.*;
import de.ovgu.featureide.fm.core.io.dimacs.*;
import de.ovgu.featureide.fm.core.io.manager.*;

public abstract class Transform extends Analysis {

	private static final long serialVersionUID = 1L;

	@Override
	public void run() throws Exception {
		parameters.transformation.run();
	}

	public static class TseytinZ3 extends Transformation {
		private static final long serialVersionUID = 1243195775258320809L;

		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			processFormulaResult(executeTransformer(formula, new CNFTseitinTransformer()));
		}
	}

	public static class TseytinSPLDev extends Transformation {
		private static final long serialVersionUID = 8198210007041611191L;

		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			CNFTransformer transformer = new CNFTransformer();
			transformer.setMaximumNumberOfLiterals(0);
			processFormulaResult(executeTransformer(formula, transformer));
		}
	}

	public static class DistribFeatureIDE extends Transformation {
		private static final long serialVersionUID = 4668156394793748450L;

		@Override
		public void run() {
			final IFeatureModel featureModel = FeatureModelManager
				.load(Paths.get(parameters.rootPath).resolve(parameters.modelPath));
			if (featureModel != null) {
				Result<CNF> result = execute(() -> new FeatureModelFormula(featureModel).getCNF());
				if (result != null) {
					printResult(result.timeNeeded);
					printResult(result.payload.getVariables().size());
					printResult(result.payload.getClauses().size());
					de.ovgu.featureide.fm.core.io.manager.FileHandler.save(getTempPath(), result.payload,
						new DIMACSFormatCNF());
				}
			}
		}
	}

	public static class DistribSPLDev extends Transformation {
		private static final long serialVersionUID = -6532557981508394209L;

		@Override
		public void run() {
			Formula formula = readFormula(Paths.get(parameters.modelPath));
			processFormulaResult(executeTransformer(formula, new CNFTransformer()));
		}
	}
}
