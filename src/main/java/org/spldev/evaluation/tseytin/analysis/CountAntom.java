package org.spldev.evaluation.tseytin.analysis;

import java.math.BigInteger;
import java.util.stream.Stream;

public class CountAntom extends Analysis.ProcessAnalysis<String> {
	public CountAntom() {
		useTimeout = true;
	}

	@Override
	String[] getCommand() {
		final String[] command = new String[2];
		command[0] = "ext-libs/countAntom";
		// command[1] = "--doTseitin=no";
		command[1] = getTempPath().toString();
		return command;
	}

	@Override
	String getDefaultResult() {
		return "NA";
	}

	@Override
	String getResult(Stream<String> lines) {
		return lines.filter(line -> line.startsWith("c model count"))
			.map(line -> line.split(":")[1].trim())
			.findFirst()
			.orElse(getDefaultResult());
	}
}
