#!/bin/sh
##############################################################################
#
#   Copyright 2004 The Apache Software Foundation.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
##############################################################################

### BEGIN INIT INFO
# Provides: appbrokers
# Required-Start: $network 
# Required-Stop: $network 
# Default-Start: 3 5
# Default-Stop: 0 1 2 6
# Description: Start the eXist database
### END INIT INFO

#
# Small shell script to show how to start/stop eXist using jsvc
#
# Adapt the following lines to your configuration

JAVA_HOME=/usr/java/jdk1.5.0_03/
EXIST_HOME=/opt/exist
DAEMON_HOME=$EXIST_HOME/bin
EXIST_USER=exist

# for multi instances adapt those lines.
TMP_DIR=/var/tmp
PID_FILE=$EXIST_HOME/jsvc.pid

JAVA_ENDORSED_DIR=$EXIST_HOME/lib/endorsed
#LDAP_OPTIONS="-Dsecurity.ldap.connection.url=ldap://your-server.com:389 -Dsecurity.ldap.dn.user=ou=Users,dc=yourdomain,dc=org,dc=authority -Dsecurity.ldap.dn.group=ou=Groups,dc=yourdomain,dc=org,dc=authority"
JAVA_OPTIONS="-Dexist.home=$EXIST_HOME -Djava.library.path=$EXIST_HOME/jni/native/.libs -Xmx512m -Dfile.encoding=UTF-8 $LDAP_OPTIONS"

CLASSPATH=\
$EXIST_HOME/bin/commons-daemon.jar:\
$EXIST_HOME/start.jar

. /etc/rc.status

# First reset status of this service
rc_reset


case "$1" in
  start)
    #
    # Start eXist
    #
    $DAEMON_HOME/jsvc \
    -user $EXIST_USER \
    -home $JAVA_HOME \
    -Djava.endorsed.dir=$JAVA_ENDORSED_DIR \
    -Djava.io.tmpdir=$TMP_DIR \
    -wait 10 \
    -pidfile $PID_FILE \
    -outfile $EXIST_HOME/logs/exist.out \
    -errfile '&1' \
    $JAVA_OPTIONS \
    -cp $CLASSPATH \
    org.exist.start.ServiceDaemon standalone
    #
    # To get a verbose JVM
    #-verbose \
    # To get a debug of jsvc.
    #-debug \

    rc_status -v

    exit $?
    ;;

  stop)
    #
    # Stop eXist
    #
    $DAEMON_HOME/jsvc \
    -Djava.endorsed.dir=$JAVA_ENDORSED_DIR \
    -cp $CLASSPATH \
    -stop \
    -pidfile $PID_FILE \
    org.exist.start.ServiceDaemon shutdown
    rc_status -v
    exit $?
    ;;

  *)
    echo "Usage $0 start/stop"
    exit 1;;
esac
