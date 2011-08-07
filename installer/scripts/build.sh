#!/bin/bash
# $Id$

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="%{JDKPath}"
fi

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

# will be set by the installer
if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME="%{INSTALL_PATH}"
fi

ANT_HOME="$EXIST_HOME/tools/ant"

LOCALCLASSPATH="$ANT_HOME/lib/ant-launcher.jar"

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

JAVA_OPTS="-Dant.home=$ANT_HOME -Dant.library.dir=$ANT_HOME/lib -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS -Dexist.home=$EXIST_HOME -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl"

echo Starting Ant...
echo

$JAVA_HOME/bin/java -Xms512m -Xmx2048m $JAVA_OPTS -classpath $LOCALCLASSPATH org.apache.tools.ant.launch.Launcher $*
