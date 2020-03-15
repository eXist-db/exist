#!/usr/bin/env bash
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
