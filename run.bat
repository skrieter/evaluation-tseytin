call mvn install
del evaluation-tseytin.jar
copy target\evaluation-tseytin-1.0-SNAPSHOT-combined.jar evaluation-tseytin.jar
java -jar evaluation-tseytin.jar eval-clean config
java -da -Xmx12g -jar evaluation-tseytin.jar eval-tseytin config