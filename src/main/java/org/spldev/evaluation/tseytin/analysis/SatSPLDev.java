package org.spldev.evaluation.tseytin.analysis;

import org.spldev.formula.ModelRepresentation;
import org.spldev.formula.analysis.sat4j.HasSolutionAnalysis;

public class SatSPLDev extends Analysis.SPLDevAnalysis {
	@Override
	public void run(ModelRepresentation rep) {
		printResult(execute(() -> {
			final long localTime = System.nanoTime();
			final Boolean sat = new HasSolutionAnalysis().getResult(rep).get();
			if (sat == null)
				return null;
			final long timeNeeded = System.nanoTime() - localTime;
			return new Result<>(timeNeeded, null, sat);
		}));
	}
}
