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

readDataSet = read.csv('pullRequestRefactoring.csv', header = TRUE, sep = ";", quote = "\"",
                       dec = ".", fill = TRUE, comment.char = "")

model=lm( readDataSet$pullRequest ~ readDataSet$refactoring )
anova(model)
ANOVA=aov(model)

TUKEY <- TukeyHSD(x=ANOVA, 'readDataSet$refactoring', conf.level=0.95)
TUKEY
plot(TUKEY , las=1 , col="brown")
