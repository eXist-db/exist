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
 : A filter expression (E)[n] on the right of "/" or "!" counts the positions of its
 : predicate within the sequence E returns for each context item, for stored nodes as
 : for nodes in memory. See https://github.com/eXist-db/exist/issues/6831
 :)
module namespace fep="http://exist-db.org/xquery/test/filter-expression-persistent";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fep:XML := document {
<root>
    <entry n="e1">
        <sense n="s1"><def n="a1"/><def n="a2"/><def n="a3"/></sense>
        <sense n="s2"><def n="b1"/><def n="b2"/></sense>
    </entry>
</root>
};

(:~ Name of the collection of this file, unique among the files of the suite. :)
declare variable $fep:COLLECTION_NAME := "test-filter-expression-persistent";

(:~ Full path of the collection of this file. :)
declare variable $fep:COLLECTION := "/db/" || $fep:COLLECTION_NAME;

declare
    %test:setUp
function fep:setup() {
    let $testCol := xmldb:create-collection("/db", $fep:COLLECTION_NAME)
    return
        xmldb:store($fep:COLLECTION, "test.xml", $fep:XML)
};

declare
    %test:tearDown
function fep:tearDown() {
    xmldb:remove($fep:COLLECTION)
};

declare %private function fep:senses() {
    doc($fep:COLLECTION || "/test.xml")//sense
};

declare
    %test:assertEquals("a1", "b1")
function fep:child-first() {
    fep:senses()/(def)[1]/@n/string()
};

declare
    %test:assertEquals("a2", "b2")
function fep:child-second() {
    fep:senses()/(def)[2]/@n/string()
};

declare
    %test:assertEquals("a3", "b2")
function fep:child-last() {
    fep:senses()/(def)[last()]/@n/string()
};

declare
    %test:assertEquals("a1", "b1")
function fep:child-variable-position() {
    let $n := 1
    return fep:senses()/(def)[$n]/@n/string()
};

declare
    %test:assertEquals("a1", "b1")
function fep:child-position-function() {
    fep:senses()/(def)[position() = 1]/@n/string()
};

declare
    %test:assertEquals("a1", "b1")
function fep:descendant-first() {
    fep:senses()/(descendant::def)[1]/@n/string()
};

declare
    %test:assertEquals("a2", "b2")
function fep:child-with-predicate-first() {
    fep:senses()/(def[@n = ("a2", "a3", "b2")])[1]/@n/string()
};

declare
    %test:assertEquals("e1")
function fep:ancestor-first() {
    fep:senses()/(ancestor::entry)[1]/@n/string()
};

declare
    %test:assertEquals("e1")
function fep:parent-or-self-first() {
    fep:senses()/(.., .)[1]/@n/string()
};

declare
    %test:assertEquals("s2")
function fep:following-sibling-first() {
    fep:senses()/(following-sibling::*)[1]/@n/string()
};

declare
    %test:assertEquals("a2", "b2")
function fep:sequence-with-duplicates() {
    fep:senses()/(def, def)[2]/@n/string()
};

declare
    %test:assertEquals("e1", "e1")
function fep:simple-map-ancestor-first() {
    fep:senses() ! (ancestor::entry)[1]/@n/string()
};

declare
    %test:assertEquals("a1", "b1")
function fep:simple-map-child-first() {
    fep:senses() ! (def)[1]/@n/string()
};

(:~ A filter on a variable doesn't depend on the context items: it is evaluated once, not for each of them. :)
declare
    %test:assertEquals("a1")
function fep:variable-filter-as-argument-in-predicate() {
    let $names := ("a1", "b1")
    return fep:senses()/def[string-join($names[1], ",") = @n]/@n/string()
};

declare
    %test:assertEquals("a1", "b1")
function fep:child-first-in-memory() {
    $fep:XML//sense/(def)[1]/@n/string()
};

declare
    %test:assertEquals("e1")
function fep:ancestor-first-in-memory() {
    $fep:XML//sense/(ancestor::entry)[1]/@n/string()
};
