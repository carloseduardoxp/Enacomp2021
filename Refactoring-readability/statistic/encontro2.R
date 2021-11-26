if(!require(psych)){install.packages("psych")}
if(!require(FSA)){install.packages("FSA")}
if(!require(lattice)){install.packages("lattice")}
if(!require(BSDA)){install.packages("BSDA")}
if(!require(rcompanion)){install.packages("rcompanion")}
if(!require(coin)){install.packages("coin")}
if(!require(DescTools)){install.packages("DescTools")}
if(!require(effsize)){install.packages("effsize")}

setwd(dirname(rstudioapi::getActiveDocumentContext()$path))

options(warn=1)

readDataSet = read.csv('pullRequest.csv', header = TRUE, sep = ";", quote = "\"",
                       dec = ".", fill = TRUE, comment.char = "")

types <- readDataSet[,1, drop=FALSE]
types = unique(types)


wilcox.test(operation ~ readability, data = readDataSet, conf.int = TRUE, conf.level = 0.95)

wilcoxonPairedR(x = ref$readability, g = ref$operation)

boxplot(operation ~ readability, data = readDataSet)

