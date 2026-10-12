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

module namespace test-registered-indexes="http://exist-db.org/xquery/test/system/registered-indexes";

import module namespace system="http://exist-db.org/xquery/system";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEmpty
function test-registered-indexes:missing-keys() {
    let $info := system:get-registered-indexes()
    return $info[not(
        map:contains(., "id") and map:contains(., "class")
        and map:contains(., "registration-source") and map:contains(., "enabled")
    )]
};

declare
    %test:assertEmpty
function test-registered-indexes:invalid-registration-source() {
    system:get-registered-indexes()[not(map:get(., "registration-source") = ("built-in", "spi", "conf.xml"))]
};

declare
    %test:assertEmpty
function test-registered-indexes:invalid-enabled() {
    system:get-registered-indexes()[not(map:get(., "enabled") = ("yes", "no"))]
};

(: The structural index is always registered directly by IndexManager, not via conf.xml or
 : SPI, so it's the one entry every instance reports regardless of which index extension
 : jars are on the classpath - with source "built-in", matching ModuleRegistration's
 : "built-in" for fn: (see IndexManager#registerStructuralIndexProvenance). :)
declare
    %test:assertEquals("built-in", "yes")
function test-registered-indexes:structural-index-is-built-in() {
    let $entry := system:get-registered-indexes()[map:get(., "id") = "structural-index"]
    return (map:get($entry, "registration-source"), map:get($entry, "enabled"))
};

(: exist-core's own test run has none of the bundled index extension jars (lucene, range,
 : ngram, sort, spatial - separate Maven modules) on its classpath, so no SPI IndexFactory
 : or conf.xml-configured index is discoverable here beyond the always-present structural
 : index. Extension-specific "does my SPI-registered index show up" coverage belongs in
 : each extension's own test suite, against system:get-registered-indexes(). :)

(: Only error state: a caller without the DBA role is refused outright. :)
declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-registered-indexes:guest-is-denied() {
    system:get-registered-indexes()
};
