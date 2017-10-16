#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# backup.sh - Backup tool start script
#
# $Id: backup.sh 10899 2009-12-28 18:07:14Z dizzzz $
# -----------------------------------------------------------------------------

## @UNIX_INSTALLER_1@ 

#
# In addition to the other parameter options for the interactive client 
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
source "${SCRIPTPATH}"/eXist-settings.sh
source "${SCRIPTPATH}"/jmx-settings.sh
source "${SCRIPTPATH}"/getopt-settings.sh

get_opts "$@";

check_exist_home "$0";

set_exist_options;

# set java options
set_client_java_options;

# enable the JMX agent? If so, concat to $JAVA_OPTIONS:
check_jmx_status;

# save LANG
set_locale_lang;

"${JAVA_HOME}"/bin/java ${JAVA_OPTIONS} ${OPTIONS} ${DEBUG_OPTS} -cp ${EXIST_BACKREST_HOME}/lib/exist-backrest.jar org.exist.backup.Main "${JAVA_OPTS[@]}"

restore_locale_lang;
