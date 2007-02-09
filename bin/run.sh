#!/bin/bash
#unset LANG

YJP_HOME=/home/wolf/Java/yjp-5.5.2

if [ -z "$EXIST_HOME" ]; then
    EXIST_HOME_1=`dirname "$0"`
    EXIST_HOME=`dirname "$EXIST_HOME_1"`
fi

if [ ! -f "$EXIST_HOME/conf.xml" ]; then
    EXIST_HOME_1="$EXIST_HOME/.."
    EXIST_HOME=$EXIST_HOME_1
fi

if [ -z "$EXIST_BASE" ]; then
    EXIST_BASE=$EXIST_HOME
fi

if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xms64000k -Xmx128000k -Dfile.encoding=UTF-8"
fi

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

PROFILER_OPTS=-agentlib:yjpagent
LD_LIBRARY_PATH="$YJP_HOME/bin/linux-amd64/"

$JAVA_HOME/bin/java $JAVA_OPTS \
	-Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS \
	-Dexist.home=$EXIST_HOME $PROFILER_OPTS \
	-jar start.jar $*