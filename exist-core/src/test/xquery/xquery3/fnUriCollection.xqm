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

module namespace fnuc="http://exist-db.org/xquery/test/function_uri_collection";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace x="httpx://x/ns/1.0";

declare variable $fnuc:COLLECTION_NAME := "/test-collection";
declare variable $fnuc:SUBCOLLECTION_NAME := $fnuc:COLLECTION_NAME||"/subcol";
declare variable $fnuc:COLLECTION := collection("/db"||$fnuc:COLLECTION_NAME);
declare variable $fnuc:SUBCOLLECTION := collection("/db"||$fnuc:SUBCOLLECTION_NAME);

declare
    %test:setUp
function fnuc:setup() {
    let $collection := xmldb:create-collection("/db", "test-collection")

    return
        (
            xmldb:create-collection("/db", $fnuc:SUBCOLLECTION_NAME),
            xmldb:store("/db"||$fnuc:SUBCOLLECTION_NAME, "test-subcol.xml", <container><a/><b/></container>),
            xmldb:store($collection, "test.bin", "binary", "application/octet-stream"),
            xmldb:store($collection, "test.xml", document { <container><a/><b/></container>})
        )
};

declare
    %test:tearDown
function fnuc:cleanup() {
    xmldb:remove("/db/test-collection")
};

declare
    %test:assertEquals("/db")
function fnuc:no-argument() {
    fn:uri-collection()
};

declare
    %test:assertError("FODC0004")
function fnuc:invalid-uri() {
    fn:uri-collection(":invalid-uri")
};

declare
    %test:assertEquals("/db/test-collection/test.bin", "/db/test-collection/test.xml", "/db/test-collection/subcol")
function fnuc:all-uris() {
    fn:uri-collection("/db/test-collection")
};

declare
    %test:assertEquals("/db/test-collection/subcol")
function fnuc:subcollection-uris() {
    fn:uri-collection("/db/test-collection?content-type=application/vnd.existdb.collection")
};

declare
    %test:assertEquals("/db/test-collection/test.bin", "/db/test-collection/test.xml")
function fnuc:document-uris() {
    fn:uri-collection("/db/test-collection?content-type=application/vnd.existdb.document")
};

declare
    %test:assertEquals("/db/test-collection/test.xml")
function fnuc:xml-document-uris() {
    fn:uri-collection("/db/test-collection?content-type=application/vnd.existdb.document+xml")
};

declare
    %test:assertEquals("/db/test-collection/test.bin")
function fnuc:binary-document-uris() {
    fn:uri-collection("/db/test-collection?content-type=application/vnd.existdb.document+binary")
};

declare
    %test:assertEquals("/db/test-collection/test.bin")
function fnuc:match-uris() {
    fn:uri-collection("/db/test-collection?match=.*\.bin")
};

declare
    %test:assertEmpty
function fnuc:no-match-uris() {
    fn:uri-collection("/db/test-collection?match=.*\.nonexisting")
};

declare
    %test:assertEquals("/db/test-collection/test.bin", "/db/test-collection/test.xml", "/db/test-collection/subcol")
function fnuc:stable() {
    let $c1 := fn:uri-collection("/db/test-collection?stable=yes")
    let $r  := xmldb:remove("/db/test-collection", "test.xml")
    let $c2 := fn:uri-collection("/db/test-collection?stable=yes")
    let $a  := xmldb:store("/db/test-collection", "test.xml", document { <container><a/><b/></container>})
    return $c2
};

declare
    %test:assertEquals("/db/test-collection/test.bin", "/db/test-collection/subcol")
function fnuc:not-stable() {
    let $c1 := fn:uri-collection("/db/test-collection?stable=no")
    let $r  := xmldb:remove("/db/test-collection", "test.xml")
    let $c2 := fn:uri-collection("/db/test-collection?stable=no")
    let $a  := xmldb:store("/db/test-collection", "test.xml", document { <container><a/><b/></container>})
    return $c2
};
