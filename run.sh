#! /bin/bash
set -e
JAR=evaluation-tseytin-1.0-SNAPSHOT-combined.jar
java -jar ${JAR} eval-clean config
java -da -Xmx12g -jar ${JAR} eval-tseytin config
