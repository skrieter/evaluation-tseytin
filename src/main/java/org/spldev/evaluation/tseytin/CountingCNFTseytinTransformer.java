package org.spldev.evaluation.tseytin;

import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.transform.CNFTseytinTransformer;

import java.util.ArrayList;

public class CountingCNFTseytinTransformer extends CNFTseytinTransformer {
	private int numberOfTseytinTransformedClauses = 0;

	public CountingCNFTseytinTransformer(int maximumNumberOfClauses, int maximumLengthOfClauses) {
		super(maximumNumberOfClauses, maximumLengthOfClauses);
	}

	@Override
	public void tseytin(final ArrayList<Formula> newChildren, Formula child) {
		super.tseytin(newChildren, child);
		if (!stack.isEmpty()) {
			numberOfTseytinTransformedClauses++;
		}
		numberOfTseytinTransformedClauses += substitutes.size();
	}

	public int getNumberOfTseytinTransformedClauses() {
		return numberOfTseytinTransformedClauses;
	}
}
