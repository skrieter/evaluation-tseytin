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

import java.io.*;

import org.spldev.evaluation.tseytin.analysis.*;

public class Parameters implements Serializable {
	private static final long serialVersionUID = 1L;
	public String system;
	public String rootPath;
	public String modelPath;
	public int iteration;
	public String tempPath;
	public long timeout;
	public Analysis transformation;

	public Parameters(String system, String rootPath, String modelPath, int iteration, String tempPath,
		long timeout) {
		this.system = system;
		this.rootPath = rootPath;
		this.modelPath = modelPath;
		this.iteration = iteration;
		this.tempPath = tempPath;
		this.timeout = timeout;
	}

	@Override
	public String toString() {
		return String.format(
			"Parameters{rootPath='%s', modelPath='%s', iteration=%d, tempPath='%s', timeout=%d, transformation=%s}",
			rootPath, modelPath, iteration, tempPath, timeout, transformation);
	}
}
