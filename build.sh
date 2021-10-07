#! /bin/bash

# to use Z3 for Tseytin transformation, you must install Z3 AND its Java bindings:
# git clone https://github.com/Z3Prover/z3
# cd z3
# python scripts/mk_make.py --java
# cd build
# make
# sudo make install

set -e
mvn clean install

#if [ ! -z "$1" ]; then
#  echo $1 > config/models.txt
#else
#  cat config/all_models.txt > config/models.txt
#fi
