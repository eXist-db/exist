#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id: startup.sh,v 1.6 2002/12/28 17:37:22 wolfgang_m Exp $
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

unset LANG
OPTIONS=

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

# use xerces as SAX parser
SAXFACTORY=org.apache.xerces.jaxp.SAXParserFactoryImpl

# set java options
if [ -z "$JAVA_OPTIONS" ]; then
    export JAVA_OPTIONS="-Xms32000k -Xmx256000k -Djavax.xml.parsers.SAXParserFactory=$SAXFACTORY -Dfile.encoding=UTF-8 -Dexist.start.debug=true"
fi

$JAVA_HOME/bin/java $JAVA_OPTIONS $OPTIONS -jar "$EXIST_HOME/start.jar" backup $*
