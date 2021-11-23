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

readDataSet = read.csv('saida.csv', header = TRUE, sep = ";", quote = "\"",
                       dec = ".", fill = TRUE, comment.char = "")

types <- readDataSet[,1, drop=FALSE]
types = unique(types)

ref = subset(readDataSet, refactoring_type=='Add Method Annotation')

wilcox.test(readability ~ operation, data = ref, conf.int = TRUE, conf.level = 0.95)
wilcox.test(understandability ~ operation, data = ref, conf.int = TRUE, conf.level = 0.95)

wilcoxonPairedR(x = ref$readability, g = ref$operation)
wilcoxonPairedR(x = ref$understandability, g = ref$operation)

boxplot(readability ~ operation, data = ref)
boxplot(understandability ~ operation, data = ref)

