#!/bin/bash
# -----------------------------------------------------------------------------
# backup.sh - Backup tool start script
#
# $Id: backup.sh 5792 2007-05-10 01:54:38Z ellefj $
# -----------------------------------------------------------------------------

exist_home () {
	case "$0" in
		/*)
			p=$0
		;;
		*)
			p=`/bin/pwd`/$0
		;;
	esac
		(cd `/usr/bin/dirname $p` ; /bin/pwd)
}

# will be set by the installer
if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME="%{INSTALL_PATH}"
fi

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

unset LANG
OPTIONS=

if [ ! -f "$EXIST_HOME/start.jar" ]; then
	echo "Unable to find start.jar. Please set EXIST_HOME to point to your installation directory."
	exit 1
fi

OPTIONS="-Dexist.home=$EXIST_HOME"

# set java options
if [ -z "$JAVA_OPTIONS" ]; then
	JAVA_OPTIONS="-Xms32000k -Xmx256000k -Dfile.encoding=UTF-8"
fi

$JAVA_HOME/bin/java $JAVA_OPTIONS $OPTIONS -jar "$EXIST_HOME/start.jar" backup $*
