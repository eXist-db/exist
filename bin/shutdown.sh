#!/bin/sh
# -----------------------------------------------------------------------------
# startup.sh - Start Script for the CATALINA Server
#
# $Id: shutdown.sh,v 1.5 2002/12/28 17:37:22 wolfgang_m Exp $
# -----------------------------------------------------------------------------

P=$(dirname $0)

if [ -z "$EXIST_HOME" ]; then
    P=$(dirname $0)

    if test "$P" = "." 
    then
        EXIST_HOME="`pwd`/.."
    else
        EXIST_HOME="$P/.."
    fi
fi

if [ -z "$EXIST_BASE" ]; then
    EXIST_BASE=$EXIST_HOME
fi

LOCALCLASSPATH=$JAVA_HOME/lib/tools.jar:$EXIST_BASE/exist.jar:$EXIST_BASE
JARS=`ls -1 $EXIST_BASE/lib/*.jar`
for jar in $JARS
do
   LOCALCLASSPATH=$jar:$LOCALCLASSPATH ;
done
OLDCP=$CLASSPATH
export CLASSPATH=$LOCALCLASSPATH

JETTY_HOME="$EXIST_HOME/Jetty-4.1.4"
$JETTY_HOME/bin/jetty.sh stop "$@"
export CLASSPATH=$OLDCP
#CATALINA_HOME="$EXIST_HOME/jakarta-tomcat-4.0.3/"
#$CATALINA_HOME/bin/catalina.sh stop "$@"