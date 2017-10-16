#!/usr/bin/env bash

# Run the HSQL Database Manager
# $Id$

if [ -z "${EXIST_HOME}" ]; then
	EXIST_HOME="../../..";
fi
# set java options
if [ -z "${CLIENT_JAVA_OPTIONS}" ]; then
    CLIENT_JAVA_OPTIONS="-Xms64m -Xmx256m -Dfile.encoding=UTF-8";
fi

HSQL_LIB="${EXIST_HOME}/extensions/indexes/spatial/lib"

if [ "x$1" = "x" ]; then
    HSQL_DATA="${EXIST_HOME}/webapp/WEB-INF/data/spatial_index"
else
    HSQL_DATA="${EXIST_HOME}/$1"
fi

JAVA_OPTIONS="${CLIENT_JAVA_OPTIONS} -cp ${HSQL_LIB}/hsqldb.jar"


${JAVA_HOME}/bin/java ${JAVA_OPTIONS} org.hsqldb.util.DatabaseManagerSwing --url jdbc:hsqldb:${HSQL_DATA}
