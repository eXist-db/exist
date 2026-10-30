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
 : Document nodes, such as fn:collection() returns, put into document order by a path expression or a
 : union: they are sorted by their documents, and each document appears once.
 :)
module namespace dno="http://exist-db.org/xquery/test/document-node-order";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $dno:COLLECTION := "/db/document-node-order";

declare
    %test:setUp
function dno:setup() {
    let $collection := xmldb:create-collection("/db", "document-node-order")
    return (
        (: stored in this order, so that their documents are in this order :)
        xmldb:store($collection, "a.xml", <root n="a"/>),
        xmldb:store($collection, "b.xml", <root n="b"/>),
        xmldb:store($collection, "c.xml", <root n="c"/>)
    )
};

declare
    %test:tearDown
function dno:cleanup() {
    xmldb:remove($dno:COLLECTION)
};

declare
    %test:assertEquals("a", "b", "c")
function dno:documents-in-document-order() {
    (doc($dno:COLLECTION || "/c.xml"), doc($dno:COLLECTION || "/a.xml"), doc($dno:COLLECTION || "/b.xml"))/root/@n/string()
};

declare
    %test:assertEquals(3)
function dno:each-document-once() {
    count((collection($dno:COLLECTION), collection($dno:COLLECTION), doc($dno:COLLECTION || "/b.xml"))/root)
};

declare
    %test:assertEquals("a", "b", "c")
function dno:collection-in-document-order() {
    collection($dno:COLLECTION)/root/@n/string()
};

declare
    %test:assertEquals("root a", "document b", "root c")
function dno:documents-and-elements-in-document-order() {
    (doc($dno:COLLECTION || "/c.xml")/root | doc($dno:COLLECTION || "/b.xml") | doc($dno:COLLECTION || "/a.xml")/root)
        ! (if (. instance of document-node()) then "document " || root/@n else "root " || @n)
};
