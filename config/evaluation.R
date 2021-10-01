library(readr)
library(tidyverse)
#systems <- read_delim("systems.csv",delim = ";", escape_double = FALSE, trim_ws = TRUE)
evaluation <- read_delim("evaluation.csv",delim = ";", escape_double = FALSE, trim_ws = TRUE)

system_ids <- function(system) {
  return(with(systems, which(startsWith(System, "busybox/1_9_1.kconfigreader.model"))))
}

#with(dataset, data.frame(num = MaxNumOfClauses[order(SatTime)], len = MaxLenOfClauses[order(SatTime)]))

#mat = with(evaluation, matrix(Variables[ID == 178], nrow = 6, ncol = 9, byrow = TRUE))

mat = with(evaluation, matrix(AnalysisTime[System == "axtls/release-1.0.0.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))
#mat = with(evaluation, matrix(Variables[System == "axtls/release-1.0.0.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))
#mat = with(evaluation, matrix(Clauses[System == "axtls/release-1.0.0.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))
#mat = with(evaluation, matrix(TseytinClauses[System == "axtls/release-1.0.0.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))

#mat = with(dataset, matrix(AnalysisTime[System == "buildroot/2009.11.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))
image(mat)
