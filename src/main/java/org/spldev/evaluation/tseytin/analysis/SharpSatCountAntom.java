package org.spldev.evaluation.tseytin.analysis;

import java.util.stream.Stream;

public class SharpSatCountAntom extends Analysis.ProcessAnalysis<String> {
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
