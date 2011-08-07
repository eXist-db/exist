#!/bin/bash
# $Id$

if [ -z "$JAVA_HOME" ]; then
  echo "ERROR: JAVA_HOME not found in your environment."
  echo
  echo "Please, set the JAVA_HOME variable in your environment to match the"
  echo "location of the Java Virtual Machine you want to use."
  exit 1
fi

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

if [ -z "$EXIST_HOME" ]; then
    P=$(dirname $0)

    if test "$P" = "." 
    then
        EXIST_HOME="`pwd`"
    else
        EXIST_HOME="$P"
    fi
fi

ANT_HOME="$EXIST_HOME/tools/ant"

LOCALCLASSPATH="$ANT_HOME/lib/ant-launcher.jar"

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

JAVA_OPTS="-Dant.home=$ANT_HOME -Dant.library.dir=$ANT_HOME/lib -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS -Dexist.home=$EXIST_HOME -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl"

echo Starting Ant...
echo

$JAVA_HOME/bin/java -Xms512m -Xmx2048m $JAVA_OPTS -classpath $LOCALCLASSPATH org.apache.tools.ant.launch.Launcher $*
