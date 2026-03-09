#!/usr/bin/env sh
#
# eXist-db Open Source Native XML Database
# Copyright (C) 2001 The eXist-db Authors
#
# info@exist-db.org
# http://www.exist-db.org
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
#


# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls_out=$(ls -ld "$PRG")
  link=$(expr "$ls_out" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done

# Get the script name (basename of $0, which could be a symlink)
SCRIPT_NAME=$(basename "$0")
SHORT_NAME="${SCRIPT_NAME%.sh}"
PRGDIR=$(dirname "$PRG")
BASEDIR=$(cd "$PRGDIR/.." >/dev/null; pwd)

# Set variables based on the symbolic link name
case "$SHORT_NAME" in
  client|backup|export-gui|jmxclient|launcher|shutdown)
      EXIST_COMMAND=$SHORT_NAME
      ;;
  startup)
      EXIST_COMMAND="jetty"
      ;;
  export)
      EXIST_COMMAND="export --export --zip"
      ;;
  *)
      EXIST_COMMAND="launch"
      ;;
esac

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) if [ -z "$JAVA_VERSION" ] ; then
             JAVA_VERSION="CurrentJDK"
           else
             echo "Using Java version: $JAVA_VERSION"
           fi
           if [ -z "$JAVA_HOME" ]; then
              if [ -x "/usr/libexec/java_home" ]; then
                  JAVA_HOME=$(/usr/libexec/java_home)
              else
                  JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/${JAVA_VERSION}/Home
              fi
           fi
           ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=$(java-config --jre-home)
  fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=$(cygpath --unix "$JAVA_HOME")
  [ -n "$CLASSPATH" ] && CLASSPATH=$(cygpath --path --unix "$CLASSPATH")
fi

# If a specific java binary isn't specified search for the standard 'java' binary
if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    # Prefer POSIX-compliant lookup for 'java' over 'which'
    if command -v java >/dev/null 2>&1; then
      JAVACMD=$(command -v java)
    else
      JAVACMD=$(which java)
    fi
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." 1>&2
  echo "  We cannot execute $JAVACMD" 1>&2
  exit 1
fi

REPO="$BASEDIR"/lib
CLASSPATH="$BASEDIR/etc:$REPO/*"

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$CLASSPATH" ] && CLASSPATH=$(cygpath --path --windows "$CLASSPATH")
  [ -n "$JAVA_HOME" ] && JAVA_HOME=$(cygpath --path --windows "$JAVA_HOME")
  [ -n "$HOME" ] && HOME=$(cygpath --path --windows "$HOME")
  [ -n "$BASEDIR" ] && BASEDIR=$(cygpath --path --windows "$BASEDIR")
  [ -n "$REPO" ] && REPO=$(cygpath --path --windows "$REPO")
fi

_JAVA_OPTS='-Xms256m -XX:+UseNUMA -XX:+UseZGC -XX:+UseStringDeduplication -Dfile.encoding=UTF-8'
if [ -n "$JAVA_OPTS" ] ; then
  _JAVA_OPTS="$_JAVA_OPTS $JAVA_OPTS"
fi

_EXIST_OPTS="-Dlog4j.configurationFile=$BASEDIR/etc/log4j2.xml -Dexist.home=$BASEDIR \
             -Dexist.configurationFile=$BASEDIR/etc/conf.xml   -Djetty.home=$BASEDIR \
             -Dexist.jetty.config=$BASEDIR/etc/jetty/standard.enabled-jetty-configs"
if [ -n "$EXIST_OPTS" ] ; then
  _EXIST_OPTS="$_EXIST_OPTS $EXIST_OPTS"
fi

if [ -n "$DEBUG" ] ; then
  echo "################"
  echo JAVA_OPTS="$_JAVA_OPTS"
  echo "################"
  echo EXIST_OPTS="$_EXIST_OPTS"
  echo "################"
  echo EXIST_COMMAND="$EXIST_COMMAND"
  echo "################"
fi

# Dot not double quote variables below

# shellcheck disable=SC2086
exec "$JAVACMD" $_JAVA_OPTS $_EXIST_OPTS \
  -classpath "$CLASSPATH" \
  org.exist.start.Main $EXIST_COMMAND \
  "$@"