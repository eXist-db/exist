#!/bin/bash

if [ -z "$EXIST_HOME" ]; then
    P=$(dirname $0)

    if test "$P" = "." 
    then
        EXIST_HOME="`pwd`/.."
    else
        EXIST_HOME="$P/.."
    fi
fi

LOCALCLASSPATH=$JAVA_HOME/lib/tools.jar:$EXIST_HOME/exist.jar:$EXIST_HOME

JARS=`ls -1 $EXIST_HOME/lib/core/*.jar`
for jar in $JARS
do
   LOCALCLASSPATH=$jar:$LOCALCLASSPATH ;
done

SAXFACTORY=org.apache.xerces.jaxp.SAXParserFactoryImpl

#LOCALCLASSPATH=$CLASSPATH:$LOCALCLASSPATH

$JAVA_HOME/bin/java -Xms128000k -Xmx256000k -Djavax.xml.parsers.SAXParserFactory=$SAXFACTORY -Dexist.home=$EXIST_HOME -classpath $LOCALCLASSPATH org.exist.CommandLine $*
