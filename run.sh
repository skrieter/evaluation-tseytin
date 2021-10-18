#! /bin/bash
set -e
JAR=evaluation-tseytin-1.0-SNAPSHOT-combined.jar
java -jar $JAR eval-clean config
java -da -Xmx12g -cp "$JAR:ext-libs/*" org.spldev.util.cli.CLI eval-tseytin config
