#!/bin/bash
# $Id$

JAVA_RUN="$JAVA_HOME/bin/java"

if [ "$JAVA_HOME" = "" ] ; then
  JAVA_RUN="java"
  echo "WARNING: JAVA_HOME not found in your environment."
# This should be an error according to the java guidelines, stop changing it. /ljo
#  echo
#  echo "Please, set the JAVA_HOME variable in your environment to match the"
#  echo "location of the Java Virtual Machine you want to use."
#  exit 1
fi

#if [ ! -d "$JAVA_HOME" ]; then
#    JAVA_HOME="%{JAVA_HOME}"
#fi

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

LOCALCLASSPATH="$ANT_HOME/lib/ant-launcher.jar:$EXIST_HOME/lib/user/svnkit.jar:$EXIST_HOME/lib/user/svnkit-cli.jar"

JAVA_ENDORSED_DIRS="$EXIST_HOME"/lib/endorsed

# You must set
# -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl
# Otherwise Ant will fail to do junitreport with Saxon, as it has a direct dependency on Xalan.
JAVA_OPTS="-Dant.home=$ANT_HOME -Dant.library.dir=$ANT_HOME/lib -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS -Dexist.home=$EXIST_HOME -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl $JAVA_OPTS"

echo Starting Ant...
echo

echo "$JAVA_RUN"

"$JAVA_RUN" -Xms512m -Xmx512m $JAVA_OPTS -classpath $LOCALCLASSPATH org.apache.tools.ant.launch.Launcher $*
