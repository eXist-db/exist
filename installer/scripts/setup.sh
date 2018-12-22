#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#
# Shell script to start up the eXist command line client.
# -----------------------------------------------------------------------------

# will be set by the installer
EXIST_APP_HOME="%{INSTALL_PATH}"
EXIST_HOME="%{INSTALL_PATH}"

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

JAVA_CMD="$JAVA_HOME/bin/java"

OPTIONS=

if [ ! -f "$EXIST_APP_HOME/start.jar" ]; then
	echo "Unable to find start.jar. EXIST_APP_HOME = $EXIST_APP_HOME"
	exit 1
fi

OPTIONS="-Dexist.home=$EXIST_HOME -Duse.autodeploy.feature=false"

# set java options
if [ -z "$JAVA_OPTIONS" ]; then
	JAVA_OPTIONS="-Xms64m -Xmx768m"
fi

"$JAVA_CMD" $JAVA_OPTIONS $OPTIONS \
    -jar "$EXIST_APP_HOME/start.jar" org.exist.installer.Setup $*
