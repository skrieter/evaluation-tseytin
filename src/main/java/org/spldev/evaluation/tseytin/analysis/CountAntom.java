package org.spldev.evaluation.tseytin.analysis;

import org.spldev.util.data.Pair;

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
	Pair<String, String> getDefaultResult() {
		return new Pair<>("NA", md5("NA"));
	}

	@Override
	Pair<String, String> getResult(Stream<String> lines) {
		String count = lines.filter(line -> line.startsWith("c model count"))
			.map(line -> line.split(":")[1].trim())
			.findFirst()
			.orElse("NA");
		return new Pair<>(count, md5(count));
	}
}
