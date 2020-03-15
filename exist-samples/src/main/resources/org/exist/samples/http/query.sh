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


QUERY=`cat $1`

REQUEST=`cat <<ENDE
<?xml version="1.0" encoding="UTF-8"?> \
<query xmlns="http://exist.sourceforge.net/NS/exist" \
    start="1" max="20"> \
    <text><![CDATA[$QUERY]]></text>
</query>
ENDE
`

echo $REQUEST | curl -H 'content-type:text/xml' -i --data-binary @- http://localhost:8080/exist/rest/db
