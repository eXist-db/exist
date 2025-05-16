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

module namespace test-system-get-main-module-load-path="http://exist-db.org/xquery/test/system/get-main-module-load-path";

import module namespace system = "http://exist-db.org/xquery/system";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $test-system-get-main-module-load-path:lib := ``[xquery version "3.1";
module namespace lib="//lib";
declare function lib:path() {
    system:get-main-module-load-path()
};
]``;

declare variable $test-system-get-main-module-load-path:main := ``[xquery version "3.1";
import module namespace lib="//lib" at 'xmldb:exist:///db/test/lib/lib.xqm';

system:get-main-module-load-path(),
lib:path()
]``;

declare
    %test:setUp
function test-system-get-main-module-load-path:setup() {
    let $testCol := xmldb:create-collection("/db", "test")
    let $indexCol := xmldb:create-collection("/db/test", "lib")
    return
        (
            xmldb:store("/db/test", "main.xq", $test-system-get-main-module-load-path:main),
            xmldb:store("/db/test/lib", "lib.xqm", $test-system-get-main-module-load-path:lib)
        )
};

declare
    %test:tearDown
function test-system-get-main-module-load-path:tearDown() {
    xmldb:remove("/db/test")
};

declare
    %test:assertEquals(".")
function test-system-get-main-module-load-path:in-test-module() {
    system:get-main-module-load-path()
};

declare
    %test:assertEquals(".")
function test-system-get-main-module-load-path:in-evaluated-string() {
    let $eval-load-paths := util:eval($test-system-get-main-module-load-path:main)
    return head($eval-load-paths)
};

declare
    %test:assertEquals(".")
function test-system-get-main-module-load-path:in-imported-library() {
    let $eval-load-paths := util:eval($test-system-get-main-module-load-path:main)
    return tail($eval-load-paths)
};
