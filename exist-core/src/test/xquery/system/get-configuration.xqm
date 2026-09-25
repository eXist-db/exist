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

module namespace test-get-configuration="http://exist-db.org/xquery/test/system/get-configuration";

import module namespace system="http://exist-db.org/xquery/system";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("exist")
function test-get-configuration:root-element-name() {
    local-name(system:get-configuration())
};

(: db-connection/@cacheSize carries no secret and must round-trip untouched between
 : system:get-configuration() and the equivalent system:get-configuration-property() lookup. :)
declare
    %test:assertTrue
function test-get-configuration:non-secret-value-round-trips() {
    system:get-configuration()/db-connection/@cacheSize = system:get-configuration-property("db-connection/@cacheSize")
};

(: util:'s evalDisabled parameter is a real, non-secret entry in the canonical conf.xml
 : (exist-distribution/src/main/config/conf.xml) - passes through unredacted. :)
declare
    %test:assertEquals("false")
function test-get-configuration:util-eval-disabled-param-unredacted() {
    system:get-configuration()//module[@uri = "http://exist-db.org/xquery/util"]
        /parameter[@name = "evalDisabled"]/@value/string()
};

(: The canonical conf.xml's sql:-pool password examples are XML comments (not live nodes),
 : so this holds vacuously here - it's an integration smoke check that get-configuration()
 : is wired to the real parsed document end-to-end. The redaction logic itself, including
 : the <parameter name="password" value="..."/> idiom, is exercised directly with real
 : credential-shaped data by ConfigurationRedactorTest (Java). :)
declare
    %test:assertEmpty
function test-get-configuration:no-plaintext-password-values-leak() {
    system:get-configuration()//parameter[matches(@name, "password", "i")][@value != "[redacted]"]
};

(: Only error state: a caller without the DBA role is refused outright. :)
declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-get-configuration:guest-is-denied() {
    system:get-configuration()
};

declare
    %test:user("guest", "guest")
    %test:assertError("exerr:ERROR")
function test-get-configuration:guest-is-denied-for-property() {
    system:get-configuration-property("db-connection/@cacheSize")
};
