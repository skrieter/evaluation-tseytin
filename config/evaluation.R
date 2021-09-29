library(readr)
library(tidyverse)
dataset <- read_delim("evaluation.csv",delim = ";", escape_double = FALSE, trim_ws = TRUE)

#> with(dataset, data.frame(num = MaxNumOfClauses[order(AnalysisTime)], len = MaxLenOfClauses[order(AnalysisTime)]))

#mat = with(dataset, matrix(Variables[System == "buildroot/2009.11.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))

#mat = with(dataset, matrix(AnalysisTime[System == "buildroot/2009.11.kconfigreader.model"], nrow = 5, ncol = 5, byrow = TRUE))
#image(mat)
