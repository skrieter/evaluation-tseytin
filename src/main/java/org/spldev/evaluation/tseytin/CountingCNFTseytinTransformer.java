package org.spldev.evaluation.tseytin;

import java.util.*;

import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.term.bool.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.formula.expression.transform.TseytinTransformer.*;
import org.spldev.util.job.*;

public class CountingCNFTseytinTransformer extends CNFTransformer {
	private int numberOfTseytinTransformedConstraints = 0;
	private int numberOfTseytinTransformedClauses = 0;

	public CountingCNFTseytinTransformer(int maximumNumberOfClauses, int maximumLengthOfClauses) {
		super(maximumNumberOfClauses, maximumLengthOfClauses);
	}

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
