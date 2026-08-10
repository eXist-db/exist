(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

module namespace test-system-get-jmx-token="http://exist-db.org/xquery/test/system/get-jmx-token";

import module namespace system="http://exist-db.org/xquery/system";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Happy path: a DBA gets back exactly one non-blank token, in the shape produced by
 : org.exist.util.UUIDGenerator#getUUIDversion4 (a standard UUID.toString()). :)
declare
    %test:assertXPath("matches($result, '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$')")
function test-system-get-jmx-token:dba-gets-token() {
    system:get-jmx-token()
};

(: The token is read from (or created once and then persisted in) the jmxservlet.token
 : file, so repeated calls within the same run must agree. :)
declare
    %test:assertTrue
function test-system-get-jmx-token:token-is-stable-across-calls() {
    system:get-jmx-token() eq system:get-jmx-token()
};

(: Only error state: a caller without the DBA role is refused outright. There is no
 : dedicated error code for this - GetJmxToken raises a plain XPathException, which
 : surfaces as the generic eXist "exerr:ERROR". :)
declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-system-get-jmx-token:guest-is-denied() {
    system:get-jmx-token()
};
