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
(: The URI of a static import and the value of an annotation have to be literals: keep them in step with $test-system-get-main-module-load-path:COLLECTION_NAME. :)
import module namespace lib="//lib" at 'xmldb:exist:///db/test-main-module-load-path/lib/lib.xqm';

system:get-main-module-load-path(),
lib:path()
]``;

(:~ Name of the collection of this file, unique among the files of the suite. :)
declare variable $test-system-get-main-module-load-path:COLLECTION_NAME := "test-main-module-load-path";

(:~ Full path of the collection of this file. :)
declare variable $test-system-get-main-module-load-path:COLLECTION := "/db/" || $test-system-get-main-module-load-path:COLLECTION_NAME;

declare
    %test:setUp
function test-system-get-main-module-load-path:setup() {
    let $testCol := xmldb:create-collection("/db", $test-system-get-main-module-load-path:COLLECTION_NAME)
    let $indexCol := xmldb:create-collection($test-system-get-main-module-load-path:COLLECTION, "lib")
    return
        (
            xmldb:store($test-system-get-main-module-load-path:COLLECTION, "main.xq", $test-system-get-main-module-load-path:main),
            xmldb:store($test-system-get-main-module-load-path:COLLECTION || "/lib", "lib.xqm", $test-system-get-main-module-load-path:lib)
        )
};

declare
    %test:tearDown
function test-system-get-main-module-load-path:tearDown() {
    xmldb:remove($test-system-get-main-module-load-path:COLLECTION)
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
