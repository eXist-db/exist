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

module namespace test-get-configuration-schema-version="http://exist-db.org/xquery/test/system/get-configuration-schema-version";

import module namespace system="http://exist-db.org/xquery/system";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEmpty
function test-get-configuration-schema-version:all-five-schemas-return-a-version() {
    let $schemas := ("conf", "collection.xconf", "descriptor", "mime-types", "controller-config")
    for $schema in $schemas
    let $version := system:get-configuration-schema-version($schema)
    where not($version castable as xs:string) or normalize-space($version) = ""
    return $schema
};

declare
    %test:assertError("err:FOER0000")
function test-get-configuration-schema-version:unknown-schema-errors() {
    system:get-configuration-schema-version("not-a-real-schema")
};

(: Only error state: a caller without the DBA role is refused outright. :)
declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-get-configuration-schema-version:guest-is-denied() {
    system:get-configuration-schema-version("conf")
};
