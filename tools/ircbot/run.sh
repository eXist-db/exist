#!/bin/sh
# $Id$

if [ -z "${EXIST}" ]; then
    EXIST="/home/existdb/exist-db.org/eXist/";
fi


CP="$EXIST/exist.jar:$EXIST/tools/ircbot/lib/pircbot.jar:$EXIST/lib/core/log4j-1.2.15.jar:$EXIST/lib/core/xmldb.jar:$EXIST/lib/core/xmlrpc-client-3.1.1.jar:$EXIST/lib/core/xmlrpc-server-3.1.1.jar:$EXIST/lib/core/xmlrpc-common-3.1.1.jar:$EXIST/lib/core/ws-commons-util-1.0.2.jar:$EXIST/tools/ircbot/classes"

echo $CP

$JAVA_HOME/bin/java -classpath $CP org.exist.irc.XBot
