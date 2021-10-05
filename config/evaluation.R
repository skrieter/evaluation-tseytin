library(readr)
library(tidyverse)
library('plot.matrix')
library(viridis)
graphics.off()

max_num = list(0,10,100,1000,10000,2147483647)
max_len = list(0,2,4,8,16,32,64,128,2147483647)
systems <- read_delim("systems.csv", delim = ";", escape_double = FALSE, trim_ws = TRUE) # read systems
dd <- read_delim("evaluation.csv", delim = ";", escape_double = FALSE, trim_ws = TRUE) # read actual data
dd <- aggregate(. ~ ID + MaxNumOfClauses + MaxLenOfClauses, data=dd, mean, na.action=na.pass) # discard iterations
dd <- dd[with(dd, order(ID, MaxNumOfClauses, MaxLenOfClauses)), ] # use same order as original data
dd$Iteration <- NULL # remove iteration column

system_ids <- function(systemPrefix) {
  return(with(systems, ID[which(startsWith(System, systemPrefix))]))
}
mean_matrix <- function(systemPrefix, column) {
  means = rowSums(matrix(column[dd$ID %in% system_ids(systemPrefix)], nrow = length(max_len) * length(max_num)))
  matrix = matrix(means, nrow = length(max_num), ncol = length(max_len), byrow = TRUE)
  rownames(matrix) <- max_num
  colnames(matrix) <- max_len
  return(matrix)
}
img <- function(main, matrix) {
  #image(t(m)[,nrow(m):1])
  par(mar=c(5.1, 4.1, 4.1, 8))
  plot(matrix, asp = TRUE, col=viridis, digits=0, text.cell=list(cex=0.6), main = main)
}

systemPrefix = "linux"
#systemPrefix = "busybox/1_34_0.kconfigreader.model"
#systemPrefix = "busybox/1_16_1.kconfigreader.model"
img("TransformTime", mean_matrix(systemPrefix, dd$TransformTime))
img("Variables", mean_matrix(systemPrefix, dd$Variables))
img("Clauses", mean_matrix(systemPrefix, dd$Clauses))
img("TseytinClauses", mean_matrix(systemPrefix, dd$TseytinClauses))
img("TseytinConstraints", mean_matrix(systemPrefix, dd$TseytinConstraints))
img("SatTime", mean_matrix(systemPrefix, dd$SatTime))