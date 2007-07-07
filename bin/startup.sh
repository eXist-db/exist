#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id$
# -----------------------------------------------------------------------------

#
# Pass -j to enable JMX agent. The port for it can be specified with -p
#
usage="startup.sh [-j] [-p jmx-port]\n"

JMX_ENABLED=0
JMX_PORT=1099
while getopts ":jp:" option
do 
  case $option in
      j ) JMX_ENABLED=1 ;;
      p ) JMX_PORT=$OPTARG;;
  esac
done

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

if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME_1=`exist_home`
	EXIST_HOME="$EXIST_HOME_1/.."
fi

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

if [ -z "$JAVA_OPTIONS" ]; then
	JAVA_OPTIONS="-Xms16000k -Xmx256000k -Dfile.encoding=UTF-8"
fi

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

#DEBUG_START="-Dexist.start.debug=true"

#Comment in the following lines to enable the JMX agent:

if [ $JMX_ENABLED -gt 0 ]; then
	JMX_OPTS="-Dcom.sun.management.jmxremote \
		-Dcom.sun.management.jmxremote.port=$JMX_PORT \
		-Dcom.sun.management.jmxremote.authenticate=false \
		-Dcom.sun.management.jmxremote.ssl=false"
	JAVA_OPTIONS="$JAVA_OPTIONS $JMX_OPTS"
fi

$JAVA_HOME/bin/java $JAVA_OPTIONS -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS \
	$DEBUG_START $OPTIONS -jar "$EXIST_HOME/start.jar" \
	jetty $*

if [ -n "$OLD_LANG" ]; then
	LANG="$OLD_LANG"
fi
