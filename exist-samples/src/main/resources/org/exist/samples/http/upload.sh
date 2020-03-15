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


if [ $# -le 1 ]
then
    echo "Usage: upload.sh filename collection [curl arguments]"
    echo "Example: upload.sh test.xml /db/my_collec --user admin:admin"
    exit 1
fi

DOC=`basename $1`
URL="http://localhost:8080/exist/rest$2/$DOC"

curl -i --upload-file $1 $URL $3 $4 $5 $6 $7 $8 $9
