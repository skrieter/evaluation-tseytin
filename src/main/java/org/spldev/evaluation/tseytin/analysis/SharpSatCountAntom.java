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

import java.util.stream.Stream;

public class SharpSatCountAntom extends Analysis.ProcessAnalysis<String> {
	private static final long serialVersionUID = 4959273729902622531L;

	public SharpSatCountAntom() {
		useTimeout = true;
	}

	@Override
	String[] getCommand() {
		final String[] command = new String[4];
		command[0] = "/usr/bin/timeout";
		command[1] = String.valueOf((int) (Math.ceil(parameters.timeout) / 1000.0));
		command[2] = "ext-libs/countAntom";
		command[3] = getTempPath().toString();
		return command;
	}

	@Override
	String getDefaultResult() {
		return "NA";
	}

	@Override
	String getPayload(Stream<String> lines) {
		return lines.filter(line -> line.startsWith("c model count"))
			.map(line -> line.split(":")[1].trim())
			.findFirst()
			.orElse("NA");
	}

	@Override
	String getMd5(String payload) {
		return md5(payload);
	}
}
