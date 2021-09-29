#! /bin/bash
set -e
mvn install
rm -f evaluation-tseytin.jar
cp target/evaluation-tseytin-1.0-SNAPSHOT-combined.jar evaluation-tseytin.jar
java -jar evaluation-tseytin.jar eval-clean config

evaluate() {
  echo Evaluating $1 ...
  echo $1 > config/models.txt
  java -da -Xmx12g -jar evaluation-tseytin.jar eval-tseytin config
}

if [ ! -z "$1" ]; then
  evaluate $1
else
  for model in $(cat config/all_models.txt | sort -V); do
    evaluate $model
  done
fi