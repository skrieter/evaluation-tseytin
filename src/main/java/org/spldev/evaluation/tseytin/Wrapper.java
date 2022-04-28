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

import java.nio.file.*;
import java.util.*;

import org.spldev.evaluation.process.*;
import org.spldev.evaluation.tseytin.analysis.Analysis;

public class Wrapper extends Algorithm<List<String>> {
	public static final String RESULT_PREFIX = "result: ";

	final Analysis analysis;
	private final ArrayList<String> results = new ArrayList<>();

	public Wrapper(Analysis analysis) {
		this.analysis = analysis;
	}

	public void setTransformation(Analysis transformation) {
		analysis.parameters.transformation = transformation;
	}

	@Override
	protected void addCommandElements() {
		Path tempPath = Paths.get(analysis.parameters.tempPath).resolve("params.dat");
		analysis.write(tempPath);
		addCommandElement("java");
		addCommandElement("-da");
		addCommandElement("-Xmx12g");
		addCommandElement("-cp");
		addCommandElement(System.getProperty("java.class.path"));
		addCommandElement(Runner.class.getCanonicalName());
		addCommandElement(tempPath.toString());
	}

	@Override
	public void postProcess() throws Exception {
		results.clear();
	}

	@Override
	public void readOutput(String line) throws Exception {
		if (line.startsWith(RESULT_PREFIX)) {
			results.add(line.replace(RESULT_PREFIX, "").trim());
		}
	}

	@Override
	public List<String> parseResults() {
		return new ArrayList<>(results);
	}

	@Override
	public String getName() {
		return "TseytinEvaluation";
	}

	@Override
	public String getParameterSettings() {
		return analysis.toString();
	}
}
