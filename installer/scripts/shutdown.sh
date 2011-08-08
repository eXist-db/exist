#!/bin/bash
# -----------------------------------------------------------------------------
# shutdown.sh - Stop Jetty + eXist
#
# $Id$
# -----------------------------------------------------------------------------

# will be set by the installer
if [ -z "$EXIST_HOME" ]; then
	EXIST_HOME="%{INSTALL_PATH}"
fi

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="%{JDKPath}"
fi

if [ ! -d "$JAVA_HOME" ]; then
    JAVA_HOME="%{JAVA_HOME}"
fi


SCRIPTPATH=$(dirname $(readlink -e "$0"))


# source common functions and settings
source "${SCRIPTPATH}"/functions.d/eXist-settings.sh
source "${SCRIPTPATH}"/functions.d/jmx-settings.sh
source "${SCRIPTPATH}"/functions.d/getopt-settings.sh

check_exist_home "$0";

set_exist_options;

# set java options
set_java_options;

"${JAVA_HOME}"/bin/java ${JAVA_OPTIONS} ${OPTIONS} -jar "$EXIST_HOME/start.jar" \
	shutdown $*
