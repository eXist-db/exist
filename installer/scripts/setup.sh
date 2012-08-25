#!/bin/bash
# -----------------------------------------------------------------------------
#
# Shell script to start up the eXist command line client.
#
# $Id$
# -----------------------------------------------------------------------------

# will be set by the installer
EXIST_HOME="%{INSTALL_PATH}"

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

JAVA_CMD="$JAVA_HOME/bin/java"

OPTIONS=

if [ ! -f "$EXIST_HOME/start.jar" ]; then
	echo "Unable to find start.jar. EXIST_HOME = $EXIST_HOME"
	exit 1
fi

OPTIONS="-Dexist.home=$EXIST_HOME"

# set java options
if [ -z "$JAVA_OPTIONS" ]; then
	JAVA_OPTIONS="-Xms64m -Xmx768m"
fi

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

$JAVA_CMD $JAVA_OPTIONS $OPTIONS \
    -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS \
    -jar "$EXIST_HOME/start.jar" org.exist.installer.Setup $*
