#!/bin/bash

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

LOCALCLASSPATH=$JAVA_HOME/lib/tools.jar:$EXIST_BASE/exist.jar:$EXIST_BASE
JARS=`ls -1 $EXIST_BASE/lib/core/*.jar $EXIST_BASE/lib/optional/*.jar`
for jar in $JARS
do
   LOCALCLASSPATH=$jar:$LOCALCLASSPATH ;
done

OLD_LD_LIBRARY_PATH=$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$EXIST_BASE/lib/core

# use xerces as SAX parser
SAXFACTORY=org.apache.xerces.jaxp.SAXParserFactoryImpl

LOCALCLASSPATH=$CLASSPATH:$LOCALCLASSPATH

if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xms128000k -Xmx256000k"
fi

#PROF=-Xrunjmp:nomethods

$JAVA_HOME/bin/java $PROF $JAVA_OPTS -Djavax.xml.parsers.SAXParserFactory=$SAXFACTORY -Dexist.home=$EXIST_HOME -classpath $LOCALCLASSPATH org.exist.InteractiveClient $*

export LD_LIBRARY_PATH=$OLD_LD_LIBRARY_PATH
