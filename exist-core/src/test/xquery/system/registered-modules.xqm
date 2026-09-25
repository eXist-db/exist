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

module namespace test-registered-modules="http://exist-db.org/xquery/test/system/registered-modules";

import module namespace system="http://exist-db.org/xquery/system";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Every map must have all five keys. :)
declare
    %test:assertEmpty
function test-registered-modules:missing-keys() {
    let $info := system:get-registered-modules()
    return $info[not(
        map:contains(., "uri") and map:contains(., "prefix") and map:contains(., "source")
        and map:contains(., "registration-source") and map:contains(., "enabled")
    )]
};

declare
    %test:assertEmpty
function test-registered-modules:invalid-source() {
    system:get-registered-modules()[not(map:get(., "source") = ("built-in", "package", "mapped"))]
};

declare
    %test:assertEmpty
function test-registered-modules:invalid-registration-source() {
    system:get-registered-modules()[not(map:get(., "registration-source")
        = ("spi", "conf.xml", "built-in", "package", "mapped"))]
};

declare
    %test:assertEmpty
function test-registered-modules:invalid-enabled() {
    system:get-registered-modules()[not(map:get(., "enabled") = ("yes", "no"))]
};

(: fn: is unconditionally registered before the SPI/conf.xml loop even runs. :)
declare
    %test:assertEquals("built-in")
function test-registered-modules:fn-is-built-in() {
    system:get-registered-modules()[map:get(., "uri") = "http://www.w3.org/2005/xpath-functions"]
        ! map:get(., "registration-source")
};

(: system: itself is one of the 27 bundled modules wired via ModuleFactory SPI (#6551),
 : with no explicit conf.xml <module> entry in the canonical config (unlike e.g. util:,
 : which keeps an explicit entry for its non-default evalDisabled parameter). :)
declare
    %test:assertEquals("spi")
function test-registered-modules:system-is-spi-registered() {
    system:get-registered-modules()[map:get(., "uri") = "http://exist-db.org/xquery/system"]
        ! map:get(., "registration-source")
};

declare
    %test:assertEquals("yes")
function test-registered-modules:system-is-enabled() {
    system:get-registered-modules()[map:get(., "uri") = "http://exist-db.org/xquery/system"]
        ! map:get(., "enabled")
};

(: Only error state: a caller without the DBA role is refused outright. :)
declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-registered-modules:guest-is-denied() {
    system:get-registered-modules()
};
