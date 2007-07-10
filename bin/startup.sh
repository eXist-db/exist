#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id$
# -----------------------------------------------------------------------------

#
# In addition to the other parameter options for the jetty container 
# pass -j or --jmx to enable JMX agent. The port for it can be specified 
# with optional port number e.g. -j1099 or --jmx=1099.
#
usage="startup.sh [-j[jmx-port]|--jmx[=jmx-port]]\n"

#DEBUG_OPTS="-Dexist.start.debug=true"

SCRIPTPATH=$(dirname `/bin/pwd`/$0)
# source common functions and settings
. ${SCRIPTPATH}/functions.d/eXist-settings.sh
. ${SCRIPTPATH}/functions.d/jmx-settings.sh
. ${SCRIPTPATH}/functions.d/getopt-settings.sh

get_opts "$*" "${JETTYCONTAINER_OPTS}";

check_exist_home $0;

set_exist_options;
set_jetty_home;

# set java options
set_java_options;

# save LANG
set_locale_lang;

# enable the JMX agent? If so, concat to $JAVA_OPTIONS:
check_jmx_status;

$JAVA_HOME/bin/java $JAVA_OPTIONS -Djava.endorsed.dirs=$JAVA_ENDORSED_DIRS \
	$DEBUG_OPTS $OPTIONS -jar "$EXIST_HOME/start.jar" \
	jetty ${JAVA_OPTS[@]}

restore_locale_lang;
