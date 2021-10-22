package org.spldev.evaluation.tseytin.analysis;

import java.util.stream.Stream;

public class SharpSatSharpSat extends Analysis.ProcessAnalysis<String> {
	public SharpSatSharpSat() {
		useTimeout = false;
	}

	@Override
	String[] getCommand() {
		final String[] command = new String[6];
		command[0] = "ext-libs/sharpSAT";
		command[1] = "-noCC";
		command[2] = "-noIBCP";
		command[3] = "-t";
		command[4] = String.valueOf((int) (Math.ceil(parameters.timeout) / 1000.0));
		command[5] = getTempPath().toString();
		return command;
	}

	@Override
	String getDefaultResult() {
		return "NA";
	}

	@Override
	String getPayload(Stream<String> lines) {
		return lines.findFirst().orElse("NA");
	}

	@Override
	String getMd5(String payload) {
		return md5(payload);
	}
}
