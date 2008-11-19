#!/bin/sh

if [ -z "${EXIST}" ]; then
    EXIST="../..";
fi


CP="$EXIST/exist.jar:$EXIST/tools/ircbot/lib/pircbot.jar:$EXIST/lib/core/log4j-1.2.15.jar:$EXIST/lib/core/xmldb.jar:$EXIST/lib/core/xmlrpc-1.2-patched.jar:$EXIST/tools/ircbot/classes"

echo $CP

$JAVA_HOME/bin/java -classpath $CP org.exist.irc.XBot
