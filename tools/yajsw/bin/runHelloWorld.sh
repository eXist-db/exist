#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# run test.HelloWorld
#
# -----------------------------------------------------------------------------

set -e

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
 
PRGDIR=`dirname "$PRG"`

# set java and conf file
source "$PRGDIR"/setenv.sh

"$java_exe" -cp "$wrapper_jar":"$wrapper_app_jar" test.HelloWorld 

