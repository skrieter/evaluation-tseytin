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
package org.spldev.evaluation.tseytin;

import java.util.*;

import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.term.bool.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.formula.expression.transform.TseytinTransformer.*;
import org.spldev.util.job.*;

public class CountingCNFTseytinTransformer extends CNFTransformer {
	protected int numberOfTseytinTransformedConstraints = 0;
	protected int numberOfTseytinTransformedClauses = 0;

	@Override
	protected List<Substitute> tseytin(Formula child, InternalMonitor monitor) {
		final TseytinTransformer tseytinTransformer = new TseytinTransformer();
		tseytinTransformer.setVariableMap(VariableMap.emptyMap());
		final List<Substitute> result = tseytinTransformer.execute(child, monitor);
		numberOfTseytinTransformedConstraints++;
		return result;
	}

	@Override
	protected Collection<? extends Formula> getTransformedClauses() {
		final List<Formula> transformedClauses = new ArrayList<>();

		transformedClauses.addAll(distributiveClauses);

		if (!tseytinClauses.isEmpty()) {
			variableMap = variableMap.clone();
			final HashMap<Substitute, Substitute> combinedTseytinClauses = new HashMap<>();
			int count = 0;
			for (final Substitute tseytinClause : tseytinClauses) {
				Substitute substitute = combinedTseytinClauses.get(tseytinClause);
				if (substitute == null) {
					substitute = tseytinClause;
					combinedTseytinClauses.put(substitute, substitute);
					final BoolVariable variable = substitute.getVariable();
					if (variable != null) {
						Optional<BoolVariable> addBooleanVariable;
						do {
							addBooleanVariable = variableMap.addBooleanVariable("__temp__" + count++);
						} while (addBooleanVariable.isEmpty());
						variable.getVariableMap().renameVariable(variable.getIndex(), addBooleanVariable.get()
							.getName());
					}
				} else {
					final BoolVariable variable = substitute.getVariable();
					if (variable != null) {
						final BoolVariable otherVariable = tseytinClause.getVariable();
						otherVariable.getVariableMap().renameVariable(otherVariable.getIndex(), variable.getName());
					}
				}
			}
			final int curClauseCount = transformedClauses.size();
			for (final Substitute tseytinClause : combinedTseytinClauses.keySet()) {
				for (final Formula formula : tseytinClause.getClauses()) {
					formula.adaptVariableMap(variableMap);
					transformedClauses.add(formula);
				}
			}
			numberOfTseytinTransformedClauses = transformedClauses.size() - curClauseCount;
		}
		return transformedClauses;
	}

	public int getNumberOfTseytinTransformedClauses() {
		return numberOfTseytinTransformedClauses;
	}

	public int getNumberOfTseytinTransformedConstraints() {
		return numberOfTseytinTransformedConstraints;
	}
}
