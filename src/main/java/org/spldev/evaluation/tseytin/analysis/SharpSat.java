package org.spldev.evaluation.tseytin.analysis;

import org.spldev.util.data.Pair;

import java.util.stream.Stream;

public class SharpSat extends Analysis.ProcessAnalysis<String> {
	public SharpSat() {
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
	Pair<String, String> getDefaultResult() {
		return new Pair<>("NA", md5("NA"));
	}

	@Override
	Pair<String, String> getResult(Stream<String> lines) {
		String count = lines.findFirst().orElse("NA");
		return new Pair<>(count, md5(count));
	}
}
