
#
#  Makefile
#
#  Copyright (C) 2015 Michael Dinolfo
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

JFLAGS = -cp src:lib/*
JC = javac
MELSRC = src/Microssa
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

MELCLASSES = \
	$(MELSRC)/Configuration.java \
	$(MELSRC)/Order.java \
	$(MELSRC)/DepthBook.java \
	$(MELSRC)/Hooks.java \
	$(MELSRC)/Logger.java \
	$(MELSRC)/Database.java \
	$(MELSRC)/OrderSocket.java \
	$(MELSRC)/PriceSocket.java \
	$(MELSRC)/FIXInterface.java \
	$(MELSRC)/MatchingEngine.java \
	$(MELSRC)/Main.java \

Microssa: \
	$(MELCLASSES:.java=.class)
	cd src; \
	jar cfm Microssa.jar MANIFEST.MF Microssa/; \
	mv Microssa.jar ../bin

default: Microssa

clean:
	$(RM) $(MELSRC)/*.class
	$(RM) bin/Microssa.jar

docs: FORCE
	cd docs; \
	lualatex Microssa-Documentation.tex; \
	lualatex Microssa-Documentation.tex; \
	rm Microssa-Documentation.aux; \
	rm Microssa-Documentation.log; \
	rm Microssa-Documentation.toc; \
	cd javadoc; \
	javadoc -private ../../src/Microssa/*.java

FORCE:
