/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evaluation of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin;

import java.nio.file.*;
import java.util.*;

import org.spldev.evaluation.process.*;

public class TseytinAlgorithm extends Algorithm<List<String>> {
	private final Path modelPath;
	private final String modelFile;
	private final int maxNum;
	private final int maxLen;
	private final int i;
	private final Path tempPath;
	private String stage;
	private final ArrayList<String> results = new ArrayList<>();

	public TseytinAlgorithm(Path modelPath, String modelFile, int maxNum, int maxLen, int i, Path tempPath) {
		this.modelPath = modelPath;
		this.modelFile = modelFile;
		this.maxNum = maxNum;
		this.maxLen = maxLen;
		this.i = i;
		this.tempPath = tempPath;
	}

	public void setStage(String stage) {
		this.stage = stage;
	}

	@Override
	protected void addCommandElements() {
		addCommandElement("java");
		addCommandElement("-da");
		addCommandElement("-Xmx12g");
		addCommandElement("-cp");
		addCommandElement(System.getProperty("java.class.path"));
		addCommandElement(TseytinRunner.class.getCanonicalName());
		addCommandElement(modelPath.toString());
		addCommandElement(modelFile);
		addCommandElement(String.valueOf(maxNum));
		addCommandElement(String.valueOf(maxLen));
		addCommandElement(String.valueOf(i));
		addCommandElement(tempPath.toString());
		addCommandElement(stage);
	}

	@Override
	public void postProcess() throws Exception {
		results.clear();
	}

	@Override
	public List<String> parseResults() {
		return new ArrayList<>(results);
	}

	@Override
	public void readOutput(String line) throws Exception {
		results.add(line);
	}

	@Override
	public String getName() {
		return "HybridTseytinTransformation";
	}

	@Override
	public String getParameterSettings() {
		return maxNum + "_" + maxLen;
	}
}
