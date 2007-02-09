#!/bin/bash
# -----------------------------------------------------------------------------
#
# Shell script to start up the eXist command line client.
#
# $Id: startup.sh,v 1.6 2002/12/28 17:37:22 wolfgang_m Exp $
# -----------------------------------------------------------------------------

# will be set by the installer
if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME="%{INSTALL_PATH}"
fi

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

JAVA_CMD="$JAVA_HOME/bin/java"

OPTIONS=

if [ ! -f "$EXIST_HOME/start.jar" ]; then
	echo "Unable to find start.jar. Please set EXIST_HOME to point to your installation directory."
	exit 1
fi

OPTIONS="-Dexist.home=$EXIST_HOME"

if [ -n "$JETTY_HOME" ]; then
	OPTIONS="-Djetty.home=$JETTY_HOME $OPTIONS"
fi

# save LANG
if [ -n "$LANG" ]; then
	OLD_LANG="$LANG"
fi
# set LANG to UTF-8
LANG=en_US.UTF-8

# set java options
if [ -z "$JAVA_OPTIONS" ]; then
	JAVA_OPTIONS="-Xms16000k -Xmx256000k -Dfile.encoding=UTF-8"
fi

# save LD_LIBRARY_PATH
if [ -n "$LD_LIBRARY_PATH" ]; then
	OLD_LIBRARY_PATH="$LD_LIBRARY_PATH"
fi
# add lib/core to LD_LIBRARY_PATH for readline support
LD_LIBRARY_PATH="$LD_LIBRARY_PATH:$EXIST_HOME/lib/core"
JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

$JAVA_CMD $JAVA_OPTIONS $OPTIONS \
    -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS \
    -jar "$EXIST_HOME/start.jar" client $*

if [ -n "$OLD_LIBRARY_PATH" ]; then
	LD_LIBRARY_PATH="$OLD_LIBRARY_PATH"
fi
if [ -n "$OLD_LANG" ]; then
	LANG="$OLD_LANG"
fi
