#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for the CATALINA Server
#
# $Id: startup.sh,v 1.6 2002/12/28 17:37:22 wolfgang_m Exp $
# -----------------------------------------------------------------------------
unset LANG

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

JETTY_HOME=$EXIST_BASE/Jetty-4.1.4
LOCALCLASSPATH=$JAVA_HOME/lib/tools.jar:$EXIST_BASE/exist.jar:$EXIST_BASE:$JETTY_HOME/etc
JARS=`ls -1 $EXIST_BASE/lib/core/*.jar $EXIST_BASE/lib/optional/*.jar $JETTY_HOME/lib/*.jar`

for jar in $JARS
do
   LOCALCLASSPATH=$jar:$LOCALCLASSPATH ;
done

EXIST_OPTS="-Dexist.home=$EXIST_HOME"
JETTY_OPTS="-Djetty.home=$JETTY_HOME"

if [ -z "$JAVA_OPTIONS" ]; then
    export JAVA_OPTIONS="-Xms128000k -Xmx256000k"
fi

echo "JAVA_OPTIONS=$JAVA_OPTIONS"

# use xerces as SAX parser
SAXFACTORY=org.apache.xerces.jaxp.SAXParserFactoryImpl

$JAVA_HOME/bin/java -cp "$LOCALCLASSPATH" $JAVA_OPTIONS $EXIST_OPTS $JETTY_OPTS org.mortbay.jetty.Server $JETTY_HOME/etc/jetty.xml
