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

(:~
 : fn:collection() over a collection tree, including document and collection names that are
 : percent-encoded in their URIs.
 :)
module namespace fncoll="http://exist-db.org/xquery/test/function_collection";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fncoll:COLLECTION := "/db/fn-collection%20test";

declare
    %test:setUp
function fncoll:setup() {
    let $collection := xmldb:create-collection("/db", "fn-collection%20test")
    let $sub := xmldb:create-collection($collection, "sub%C3%BC")
    return (
        xmldb:store($collection, "plain.xml", <doc n="1"/>),
        xmldb:store($collection, "with%20space.xml", <doc n="2"/>),
        xmldb:store($collection, "%C3%BC.xml", <doc n="3"/>),
        xmldb:store($collection, "a%2520b.xml", <doc n="4"/>),
        xmldb:store($sub, "nested%20doc.xml", <doc n="5"/>),
        xmldb:store($collection, "binary.txt", "not XML", "text/plain")
    )
};

declare
    %test:tearDown
function fncoll:cleanup() {
    xmldb:remove($fncoll:COLLECTION)
};

declare
    %test:assertEquals(5)
function fncoll:xml-documents-only-including-sub-collections() {
    count(collection($fncoll:COLLECTION))
};

declare
    %test:assertEquals(
        "/db/fn-collection%20test/%C3%BC.xml",
        "/db/fn-collection%20test/a%2520b.xml",
        "/db/fn-collection%20test/plain.xml",
        "/db/fn-collection%20test/sub%C3%BC/nested%20doc.xml",
        "/db/fn-collection%20test/with%20space.xml")
function fncoll:document-uris() {
    for $doc in collection($fncoll:COLLECTION)
    order by document-uri($doc)
    return document-uri($doc)
};

declare
    %test:assertEquals("true")
function fncoll:document-uris-resolve() {
    every $doc in collection($fncoll:COLLECTION)
    satisfies doc-available(document-uri($doc)) and doc(document-uri($doc))/doc/@n = $doc/doc/@n
};

declare
    %test:assertEquals("1 2 3 4 5")
function fncoll:document-content() {
    string-join(sort(collection($fncoll:COLLECTION)/doc/@n ! xs:integer(.)), " ")
};
