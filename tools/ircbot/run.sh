#!/bin/sh

EXIST=../..

CP="$EXIST/exist.jar:lib/pircbot.jar:$EXIST/lib/core/log4j-1.2.15.jar:$EXIST/lib/core/xmldb.jar:$EXIST/lib/core/xmlrpc-1.2-patched.jar:classes"

echo $CP

java -classpath $CP org.exist.irc.XBot
