library(readr)
library(tidyverse)
library('plot.matrix')
library(viridis)
graphics.off()

replace_na <- function(data, replacement) {
  return(data[is.na(data)] <- replacement)
}

max_num = list(0,10,100,1000,10000,2147483647)
max_len = list(0,2,4,8,16,32,64,128,2147483647)
aggregator = rowSums # rowSums or rowMeans
systems <- read_delim("systems.csv", delim = ";", escape_double = FALSE, trim_ws = TRUE) # read systems
dd <- read_delim("evaluation.csv", delim = ";", escape_double = FALSE, trim_ws = TRUE) # read actual data
dd <- aggregate(. ~ ID + MaxNumOfClauses + MaxLenOfClauses, data=dd, mean, na.action=na.pass) # discard iterations
dd <- dd[with(dd, order(ID, MaxNumOfClauses, MaxLenOfClauses)), ] # use same order as original data
dd$Iteration <- NULL # remove iteration column
# if error.log is empty, all NAs are caused by timeouts (best-case estimation!)
#dd[which(dd$MaxNumOfClauses == 0 | dd$MaxLenOfClauses == 0),] <- tidyr::replace_na(dd[which(dd$MaxNumOfClauses == 0 | dd$MaxLenOfClauses == 0),], list(TransformTime=42, SatTime=42))

fillTseytinRowAndColumn <- function(dd) {
  IDs <- unique(dd$ID)
  cols <- c("TransformTime", "Variables", "Clauses", "TseytinClauses", "TseytinConstraints", "SatTime", "Sat")
  for (ID in IDs) {
    for (num in max_num) {
      for (len in max_len) {
        row <- which(dd$ID == ID & dd$MaxNumOfClauses == num & dd$MaxLenOfClauses == len)
        if (length(row) == 0) {
          next
        }
        for (col in cols) {
          val <- dd[row,][col]
          dd[which(dd$ID == ID & dd$MaxNumOfClauses == 0),][col] <- val
        }
      }
    }
  }
  return(dd)
}

#dd <- fillTseytinRowAndColumn(dd)

#dd <- tidyr::replace_na(dd, list(TransformTime=30000000000, SatTime=30000000000))

system_ids <- function(systemPrefix) {
  return(with(systems, ID[which(startsWith(System, systemPrefix))]))
}
mean_matrix <- function(systemPrefix, column) {
  means = aggregator(matrix(column[dd$ID %in% system_ids(systemPrefix)], nrow = length(max_len) * length(max_num)))
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