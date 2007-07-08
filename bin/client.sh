#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id$
# -----------------------------------------------------------------------------

#
# In addition to the other parameter options for the interactive client 
# pass -j or --jmx to enable JMX agent. The port for it can be specified 
# with --port=1099
#
# Maybe we should refactor this to avoid using two options?

JMX_ENABLED=0
JMX_PORT=1099

declare -a JAVA_OPTS
NR_JAVA_OPTS=0
NON_JAVA_OPTS=`getopt -a -o h,j,l,C:,u:,P:,p:,o:,r:,c:,f:,g:,m:,R:,n:,O:,F:,t:,X:,T:,v,q,d,s,i,Q,N,x:: --long help,port:,jmx,local,config:,user:,password:,parse:,option:,remove:,collection:,resource:,get:,mkcol:,rmcol:,howmany:,output:,file:,threads:,xupdate:,trace:,verbose,quiet,recurse-dirs,no-gui,reindex,query,no-embedded-mode,xpath:: \
     -n 'client.sh' -- "$@"`
# fixme! option -p|--parse takes 1..n arguments!

eval set -- "$NON_JAVA_OPTS"
while true ; do
    case "$1" in
        -j|--jmx) JMX_ENABLED=1; shift ;;
        -p|--port) JMX_PORT="$2"; shift 2 ;;
        -o|--option) JAVA_OPTS[$NR_JAVA_OPTS]="'$1 $2'"; let "NR_JAVA_OPTS += 1"; shift 2 ;;
        -u|--user|-P|--password|-p|--parse|-C|--config|-r|--remove|-c|--collection|-f|--resource|-g|--get|-m|--mkcol|-R|--rmcol|-x|--xpath|-n|--howmany|-O|--output|-F|--file|-t|--threads|-X|--xupdate|-T|--trace) JAVA_OPTS[$NR_JAVA_OPTS]="'$1 $2'"; let "NR_JAVA_OPTS += 1"; shift 2 ;;
        --) shift ; break ;;
        *) JAVA_OPTS[$NR_JAVA_OPTS]="$1"; let "NR_JAVA_OPTS += 1"; shift ;;
    esac
done
# Collect the remaining arguments
for arg; do
    JAVA_OPTS[$NR_JAVA_OPTS]="$arg";
    let "NR_JAVA_OPTS += 1";
done

echo ${JAVA_OPTS[@]};


# fixme! refactor
# . functions.d/*.sh
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
#DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=4444"

# set java options
if [ -z "${CLIENT_JAVA_OPTIONS}" ]; then
    CLIENT_JAVA_OPTIONS="-Xms64m -Xmx256m -Dfile.encoding=UTF-8";
fi

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

JAVA_OPTIONS="${CLIENT_JAVA_OPTIONS} -Djava.endorsed.dirs=${JAVA_ENDORSED_DIRS}";

# The following lines enables the JMX agent:
if [ $JMX_ENABLED -gt 0 ]; then
    JMX_OPTS="-Dcom.sun.management.jmxremote \
		-Dcom.sun.management.jmxremote.port=$JMX_PORT \
		-Dcom.sun.management.jmxremote.authenticate=false \
		-Dcom.sun.management.jmxremote.ssl=false"
    JAVA_OPTIONS="$JAVA_OPTIONS $JMX_OPTS"
fi

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

${JAVA_HOME}/bin/java ${JAVA_OPTIONS} ${OPTIONS} ${DEBUG_OPTS} -jar "$EXIST_HOME/start.jar" client ${JAVA_OPTS[@]}

if [ -n "${OLD_LIBRARY_PATH}" ]; then
	LD_LIBRARY_PATH="${OLD_LIBRARY_PATH}";
	export LD_LIBRARY_PATH;
fi
if [ -n "${OLD_LANG}" ]; then
	LANG="${OLD_LANG}";
	export LANG;
fi
