#!/bin/bash
# -----------------------------------------------------------------------------
# startup.sh - Start Script for Jetty + eXist
#
# $Id$
# -----------------------------------------------------------------------------

## @UNIX_INSTALLER_1@ 

#
# In addition to the other parameter options for the standalone server 
# pass -j or --jmx to enable JMX agent.  The port for it can be specified 
# with optional port number e.g. -j1099 or --jmx=1099.
#

case "$0" in
	/*)
		SCRIPTPATH=$(dirname "$0")
		;;
	*)
		SCRIPTPATH=$(dirname "$PWD/$0")
		;;
esac

# source common functions and settings
source "${SCRIPTPATH}"/functions.d/eXist-settings.sh
source "${SCRIPTPATH}"/functions.d/jmx-settings.sh
source "${SCRIPTPATH}"/functions.d/getopt-settings.sh

get_opts "$@";

check_exist_home "$0";

set_exist_options;

check_java_home;

# set java options
set_java_options;

# enable the JMX agent? If so, concat to $JAVA_OPTIONS:
check_jmx_status;

# save LANG
set_locale_lang;

"$JAVA_HOME"/bin/java $JAVA_OPTIONS $OPTIONS -jar "$EXIST_HOME/start.jar" standalone "${JAVA_OPTS[@]}"

restore_locale_lang;
