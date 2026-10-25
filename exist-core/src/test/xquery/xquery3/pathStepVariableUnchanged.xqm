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
 : Regression tests for https://github.com/eXist-db/exist/issues/2097.
 :
 : Applying a path step to a variable bound to a sequence of stored nodes
 : replaced the variable's value with the step's context node set, so the
 : variable was afterwards in document order and without duplicates.
 : The result of the path is in document order without duplicates, but the
 : variable it starts from must be left unchanged.
 :)
module namespace t="http://exist-db.org/xquery/test/path-step-variable-unchanged";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $t:COLLECTION := "/db/test-2097";

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", "test-2097"),
    xmldb:store($t:COLLECTION, "d.xml", <r><a x="1"/><b x="2"/><c x="3"/></r>),
    xmldb:store($t:COLLECTION, "e.xml", <r><a x="4"/></r>)
};

declare
    %test:tearDown
function t:tearDown() {
    xmldb:remove($t:COLLECTION)
};

declare
    %test:assertEquals("3 1 2")
function t:stored-order-after-attribute-step() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/c, $d/a, $d/b)
    let $_ := $v/@x
    return
        string-join($v ! string(@x), " ")
};

declare
    %test:assertEquals("1 2 3")
function t:stored-path-result-in-document-order() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/c, $d/a, $d/b)
    return
        string-join($v/@x ! string(.), " ")
};

declare
    %test:assertEquals("3 1 2")
function t:in-memory-order-after-attribute-step() {
    let $m := document { <r><a x="1"/><b x="2"/><c x="3"/></r> }/r
    let $v := ($m/c, $m/a, $m/b)
    let $_ := $v/@x
    return
        string-join($v ! string(@x), " ")
};

declare
    %test:assertEquals(2)
function t:stored-duplicates-after-attribute-step() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/a, $d/a)
    let $_ := $v/@x
    return
        count($v)
};

declare
    %test:assertEquals(2)
function t:stored-duplicates-after-ancestor-step() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/a, $d/a)
    let $_ := $v/ancestor-or-self::*
    return
        count($v)
};

declare
    %test:assertEquals(1)
function t:stored-path-result-without-duplicates() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/a, $d/a)
    return
        count($v/@x)
};

declare
    %test:assertEquals("4 1")
function t:stored-order-across-documents-after-attribute-step() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $e := doc($t:COLLECTION || "/e.xml")/r
    let $v := ($e/a, $d/a)
    let $_ := $v/@x
    return
        string-join($v ! string(@x), " ")
};

declare
    %test:assertEquals("3 1 2", "3 1 2")
function t:stored-order-after-repeated-steps() {
    let $d := doc($t:COLLECTION || "/d.xml")/r
    let $v := ($d/c, $d/a, $d/b)
    let $_ := $v/@x
    let $first := string-join($v ! string(@x), " ")
    let $_ := $v/ancestor::r
    return
        ($first, string-join($v ! string(@x), " "))
};

declare
    %test:assertTrue
function t:in-memory-node-identity-across-steps() {
    let $m := <r><a x="1"/></r>
    return
        $m/a is $m/a
};
