#! /bin/bash
set -e
mvn install
rm -f evaluation-tseytin.jar
cp target/evaluation-tseytin-1.0-SNAPSHOT-combined.jar evaluation-tseytin.jar
java -jar evaluation-tseytin.jar eval-clean config

if [ ! -z "$1" ]; then
  echo $1 > config/models.txt
else
  cat config/all_models.txt > config/models.txt
fi

java -da -Xmx12g -jar evaluation-tseytin.jar eval-tseytin config