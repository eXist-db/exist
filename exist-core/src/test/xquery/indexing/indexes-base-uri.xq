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

module namespace but="http://exist-db.org/xquery/indexes/base-uri-test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $but:XML := <item/>;

declare variable $but:test-col-name := "but";
declare variable $but:test-col-uri := "/db/" || $but:test-col-name;
declare variable $but:test-col-a-name := "a";
declare variable $but:test-col-a-uri := $but:test-col-uri || "/" || $but:test-col-a-name;
declare variable $but:test-col-b-name := "b";
declare variable $but:test-col-b-uri := $but:test-col-uri || "/" || $but:test-col-b-name;

declare
    %test:setUp
function but:setup() {
    xmldb:create-collection("/db", $but:test-col-name),
    xmldb:create-collection($but:test-col-uri, $but:test-col-a-name),
    xmldb:create-collection($but:test-col-uri, $but:test-col-b-name)
};

declare
    %test:tearDown
function but:tearDown() {
    xmldb:remove($but:test-col-uri)
};

declare
    %test:pending("Each test interferes with each other test, we need to figure out how to have but:tearDown called after every test")
    %test:assertEquals("/db/but/a/data/test.xml", "/db/but/a/data/test.xml", "/db/but/b/data/test.xml")
function but:base-uri-after-collection-copy() {
    let $test-col-a-data-uri := xmldb:create-collection($but:test-col-a-uri, "data")
    let $doc-path := xmldb:store($test-col-a-data-uri, "test.xml", $but:XML)
    let $base-uri-before-copy := collection($but:test-col-uri)/item/base-uri(.)
    return

    let $_ := xmldb:copy-collection($test-col-a-data-uri, $but:test-col-b-uri)
    return
        let $base-uri-after-copy := collection($but:test-col-uri)/item/base-uri(.)
        return

            ($base-uri-before-copy, $base-uri-after-copy)
};

declare
    %test:assertEquals("/db/but/a/data/test.xml", "/db/but/b/data/test.xml")
function but:base-uri-after-collection-move() {
    let $test-col-a-data-uri := xmldb:create-collection($but:test-col-a-uri, "data")
    let $doc-path := xmldb:store($test-col-a-data-uri, "test.xml", $but:XML)
    let $base-uri-before-move := collection($but:test-col-uri)/item/base-uri(.)
    return

    let $_ := xmldb:move($test-col-a-data-uri, $but:test-col-b-uri)
    return
        let $base-uri-after-move := collection($but:test-col-uri)/item/base-uri(.)
        return

            ($base-uri-before-move, $base-uri-after-move)
};

declare
    %test:pending("Each test interferes with each other test, we need to figure out how to have but:tearDown called after every test")
    %test:assertEquals("/db/but/a/data/test.xml", "/db/but/a/data/test.xml", "/db/but/b/test.xml")
function but:base-uri-after-resource-copy() {
    let $test-col-a-data-uri := xmldb:create-collection($but:test-col-a-uri, "data")
    let $doc-path := xmldb:store($test-col-a-data-uri, "test.xml", $but:XML)
    let $base-uri-before-copy := collection($but:test-col-uri)/item/base-uri(.)
    return

    let $_ := xmldb:copy-resource($test-col-a-data-uri, "test.xml", $but:test-col-b-uri, ())
    return
        let $base-uri-after-copy := collection($but:test-col-uri)/item/base-uri(.)
        return

            ($base-uri-before-copy, $base-uri-after-copy)
};

declare
    %test:pending("Each test interferes with each other test, we need to figure out how to have but:tearDown called after every test")
    %test:assertEquals("/db/but/a/data/test.xml", "/db/but/b/test.xml")
function but:base-uri-after-resource-move() {
    let $test-col-a-data-uri := xmldb:create-collection($but:test-col-a-uri, "data")
    let $doc-path := xmldb:store($test-col-a-data-uri, "test.xml", $but:XML)
    let $base-uri-before-move := collection($but:test-col-uri)/item/base-uri(.)
    return

    let $_ := xmldb:move($test-col-a-data-uri, $but:test-col-b-uri, "test.xml")
    return
        let $base-uri-after-move := collection($but:test-col-uri)/item/base-uri(.)
        return

            ($base-uri-before-move, $base-uri-after-move)
};
