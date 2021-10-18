#! /bin/bash

# { cd ~/dbse/feature-model-repository/models; find . } | cut -d/ -f2- | grep '.kconfigreader.model\|.xml' | sort -V > config/models.txt

set -e
bash build.sh
bash run.sh
