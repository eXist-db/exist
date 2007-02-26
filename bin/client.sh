#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id$
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

if [ -z "${EXIST_HOME}" ]; then
	EXIST_HOME_1=`exist_home`;
	EXIST_HOME="$EXIST_HOME_1/..";
fi

if [ ! -f "${EXIST_HOME}/start.jar" ]; then
	echo "Unable to find start.jar. Please set EXIST_HOME to point to your installation directory.";
	exit 1;
fi

OPTIONS="-Dexist.home=$EXIST_HOME"

# set java options
if [ -z "${CLIENT_JAVA_OPTIONS}" ]; then
    CLIENT_JAVA_OPTIONS="-Xms64m -Xmx256m -Dfile.encoding=UTF-8";
fi

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

JAVA_OPTIONS="${CLIENT_JAVA_OPTIONS} -Djava.endorsed.dirs=${JAVA_ENDORSED_DIRS}";

# save LANG
if [ -n "$LANG" ]; then
    OLD_LANG="$LANG";
fi
# set LANG to UTF-8
if [ `locale -a | grep -Ei "(UTF-8|utf8)" | head -n 1` ]; then
    if [ `echo ${LANG} |grep "\."` ]; then
	LANG=$(echo ${LANG} | cut -f1 -d'.')
	LANG=${LANG}.UTF-8
    else
	LANG=${LANG}.UTF-8
    fi
else
    # UTF-8 char map is unfortunately not available but we set it anyway...
    LANG=en_US.UTF-8
fi
echo "Using locale: ${LANG}"
export LANG

# save LD_LIBRARY_PATH
if [ -n "$LD_LIBRARY_PATH" ]; then
	OLD_LIBRARY_PATH="${LD_LIBRARY_PATH}";
fi
# add lib/core to LD_LIBRARY_PATH for readline support
LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:${EXIST_HOME}/lib/core";
export LD_LIBRARY_PATH;

${JAVA_HOME}/bin/java ${JAVA_OPTIONS} ${OPTIONS} -jar "$EXIST_HOME/start.jar" client $*

if [ -n "${OLD_LIBRARY_PATH}" ]; then
	LD_LIBRARY_PATH="${OLD_LIBRARY_PATH}";
	export LD_LIBRARY_PATH;
fi
if [ -n "${OLD_LANG}" ]; then
	LANG="${OLD_LANG}";
	export LANG;
fi
