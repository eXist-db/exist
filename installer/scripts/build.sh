#!/usr/bin/env bash
# $Id$

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi

# will be set by the installer
if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME="%{INSTALL_PATH}"
fi

ANT_HOME="$EXIST_HOME/tools/ant"

LOCALCLASSPATH=$CLASSPATH:$ANT_HOME/lib/ant-launcher-1.10.4.jar:$EXIST_HOME/lib/test/junit-4.12.jar

JAVA_OPTS="-Dant.home=$ANT_HOME -Dexist.home=$EXIST_HOME -Djavax.xml.transform.TransformerFactory=net.sf.saxon.TransformerFactoryImpl $JAVA_OPTS"

echo Starting Ant...
echo

"$JAVA_HOME/bin/java" -Xms512m -Xmx512m $JAVA_OPTS -classpath $LOCALCLASSPATH org.apache.tools.ant.launch.Launcher $*
