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
 : A sibling, preceding or following step with a name test gives the same nodes every time it is evaluated in one
 : query. See https://github.com/eXist-db/exist/issues/6690
 :
 : LocationStep keeps its structural index lookup for the whole query, and the select methods stamped those nodes in
 : place with the context node that matched. On the next evaluation, a duplicate check read the stamp left by the
 : previous one and skipped every node, but only when the context node carried a predicate's context, as a node
 : reached through a predicate does.
 :
 : Each test evaluates a step four times in one FLWOR, or eight times alternating two bindings, and returns the names
 : of the nodes each evaluation selects, one evaluation per string.
 :)
module namespace are="http://exist-db.org/xquery/test/axes-repeated-evaluation";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $are:COLLECTION_NAME := "test-axes-repeated-evaluation";
declare variable $are:COLLECTION := "/db/" || $are:COLLECTION_NAME;

declare
    %test:setUp
function are:setup() {
    xmldb:create-collection("/db", $are:COLLECTION_NAME),
    xmldb:store($are:COLLECTION, "single.xml",
        <root>
            <key>K1</key>
            <wrap><a/><target/></wrap>
        </root>),
    (: two context nodes, a and b, that share the siblings first and target :)
    xmldb:store($are:COLLECTION, "shared.xml",
        <root>
            <key>K1</key>
            <wrap><first/><a/><b/><target/></wrap>
        </root>)
};

declare
    %test:tearDown
function are:tear-down() {
    xmldb:remove($are:COLLECTION)
};

(:~ A node reached through a predicate, which carries the predicate's context :)
declare %private function are:via-predicate($name as xs:string) as element() {
    (doc($are:COLLECTION || "/single.xml")//key[. = 'K1']/ancestor::root//*[local-name() = $name])[1]
};

(:~ a and b, reached through a predicate :)
declare %private function are:pair-via-predicate() as element()* {
    doc($are:COLLECTION || "/shared.xml")//key[. = 'K1']/ancestor::root//(a | b)
};

(:~ a and b, reached through a plain path :)
declare %private function are:pair-via-path() as element()* {
    doc($are:COLLECTION || "/shared.xml")/root/wrap/(a | b)
};

declare
    %test:assertEquals("a", "a", "a", "a")
function are:preceding-sibling() {
    let $n := are:via-predicate("target")
    for $i in 1 to 4
    return string-join($n/preceding-sibling::a ! local-name(), " ")
};

declare
    %test:assertEquals("target", "target", "target", "target")
function are:following-sibling() {
    let $n := are:via-predicate("a")
    for $i in 1 to 4
    return string-join($n/following-sibling::target ! local-name(), " ")
};

declare
    %test:assertEquals("a", "a", "a", "a")
function are:preceding() {
    let $n := are:via-predicate("target")
    for $i in 1 to 4
    return string-join($n/preceding::a ! local-name(), " ")
};

declare
    %test:assertEquals("target", "target", "target", "target")
function are:following() {
    let $n := are:via-predicate("a")
    for $i in 1 to 4
    return string-join($n/following::target ! local-name(), " ")
};

(:~ A node reached without a predicate was never affected :)
declare
    %test:assertEquals("a", "a", "a", "a")
function are:preceding-sibling-via-path() {
    let $n := doc($are:COLLECTION || "/single.xml")/root/wrap/target
    for $i in 1 to 4
    return string-join($n/preceding-sibling::a ! local-name(), " ")
};

(:~ The wildcard form takes another branch and was never affected :)
declare
    %test:assertEquals("a", "a", "a", "a")
function are:preceding-sibling-wildcard() {
    let $n := are:via-predicate("target")
    for $i in 1 to 4
    return string-join($n/preceding-sibling::* ! local-name(), " ")
};

(:~ Other axes were never affected :)
declare
    %test:assertEquals("wrap", "wrap", "wrap", "wrap")
function are:ancestor() {
    let $n := are:via-predicate("target")
    for $i in 1 to 4
    return string-join($n/ancestor::wrap ! local-name(), " ")
};

(:~ A name that matches no sibling stays empty :)
declare
    %test:assertEquals("", "", "", "")
function are:preceding-sibling-absent() {
    let $n := are:via-predicate("target")
    for $i in 1 to 4
    return string-join($n/preceding-sibling::absent ! local-name(), " ")
};

declare
    %test:assertEquals("target", "target", "target", "target", "target", "target", "target", "target")
function are:following-sibling-shared() {
    let $via-predicate := are:pair-via-predicate()
    let $via-path := are:pair-via-path()
    for $i in 1 to 4
    return (
        string-join($via-predicate/following-sibling::target ! local-name(), " "),
        string-join($via-path/following-sibling::target ! local-name(), " ")
    )
};

declare
    %test:assertEquals("first", "first", "first", "first", "first", "first", "first", "first")
function are:preceding-sibling-shared() {
    let $via-predicate := are:pair-via-predicate()
    let $via-path := are:pair-via-path()
    for $i in 1 to 4
    return (
        string-join($via-predicate/preceding-sibling::first ! local-name(), " "),
        string-join($via-path/preceding-sibling::first ! local-name(), " ")
    )
};

declare
    %test:assertEquals("target", "target", "target", "target", "target", "target", "target", "target")
function are:following-shared() {
    let $via-predicate := are:pair-via-predicate()
    let $via-path := are:pair-via-path()
    for $i in 1 to 4
    return (
        string-join($via-predicate/following::target ! local-name(), " "),
        string-join($via-path/following::target ! local-name(), " ")
    )
};

declare
    %test:assertEquals("first", "first", "first", "first", "first", "first", "first", "first")
function are:preceding-shared() {
    let $via-predicate := are:pair-via-predicate()
    let $via-path := are:pair-via-path()
    for $i in 1 to 4
    return (
        string-join($via-predicate/preceding::first ! local-name(), " "),
        string-join($via-path/preceding::first ! local-name(), " ")
    )
};

(:~ In a predicate, the shared sibling is found for the predicate's context node every time :)
declare
    %test:assertEquals("wrap", "wrap", "wrap", "wrap")
function are:shared-sibling-in-predicate() {
    let $root := doc($are:COLLECTION || "/shared.xml")//key[. = 'K1']/ancestor::root
    for $i in 1 to 4
    return string-join($root//wrap[(a | b)/following-sibling::target] ! local-name(), " ")
};
