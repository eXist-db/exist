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

(: https://github.com/eXist-db/exist/issues/3737 :)
module namespace fn-pos="http://exist-db.org/xquery/test/fn-position";


declare namespace test="http://exist-db.org/xquery/xqsuite";


declare variable $fn-pos:collection := 'test-fn-pos';
declare variable $fn-pos:doc := 'stored-nodes.xml';
declare variable $fn-pos:nodes := document {
    <a>
      <b n="a"/>
      <b n="b"/>
      <b n="c"/>
    </a>
};

declare
    %test:setUp
function fn-pos:setup () {
    xmldb:create-collection('/db', $fn-pos:collection),
    xmldb:store('/db/' || $fn-pos:collection, $fn-pos:doc, $fn-pos:nodes)
};

declare
    %test:tearDown
function fn-pos:teardown () {
    xmldb:remove('/db/' || $fn-pos:collection, $fn-pos:doc)
};

declare
    %test:assertEquals(1,1,2,3)
function fn-pos:test-in-memory-dom() {
    $fn-pos:nodes/a/position(),
    $fn-pos:nodes//b/position()
};

declare
    %test:assertEquals(1,1,2,3)
function fn-pos:test-in-memory-dom-with-predicates() {
    $fn-pos:nodes/a[true()]/position(),
    $fn-pos:nodes//b[true()]/position()
};

declare
    %test:assertEquals(1,1)
function fn-pos:test-in-memory-dom-with-positional-predicates() {
    $fn-pos:nodes/a[1]/position(),
    $fn-pos:nodes//b[3]/position()
};

declare
    %test:assertEquals(1,1,2,3)
function fn-pos:test-persistent-dom() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return (
        $dom/a/position(),
        $dom//b/position()
    )
};

declare
    %test:assertEquals(1,1,2,3)
function fn-pos:test-persistent-dom-with-predicates() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return (
        $dom/a[true()]/position(),
        $dom//b[true()]/position()
    )
};

declare
    %test:assertEquals(1,1)
function fn-pos:test-persistent-dom-with-positional-predicates() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return (
        $dom/a[1]/position(),
        $dom//b[3]/position()
    )
};

(: https://github.com/eXist-db/exist/issues/6854 :)
declare
    %test:assertEquals("1/3 2/3 3/3")
function fn-pos:in-memory-dom-position-and-last-in-path-step() {
    string-join($fn-pos:nodes//b/(position() || '/' || last()), ' ')
};

declare
    %test:assertEquals("1/3 2/3 3/3")
function fn-pos:persistent-dom-position-and-last-in-path-step() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return string-join($dom//b/(position() || '/' || last()), ' ')
};

declare
    %test:assertEquals("1/3,2/3,3/3 | 1/3,2/3,3/3")
function fn-pos:in-memory-dom-position-and-last-in-nested-path() {
    string-join((<x/>, <y/>)/string-join($fn-pos:nodes//b/(position() || '/' || last()), ','), ' | ')
};

declare
    %test:assertEquals("1/3,2/3,3/3 | 1/3,2/3,3/3")
function fn-pos:persistent-dom-position-and-last-in-nested-path() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return string-join((<x/>, <y/>)/string-join($dom//b/(position() || '/' || last()), ','), ' | ')
};

declare
    %test:assertEquals("c", "a")
function fn-pos:in-memory-dom-position-after-sibling-path() {
    $fn-pos:nodes//b/(if (position() = last()) then @n/string() else ()),
    $fn-pos:nodes//b/(if (position() = 1) then @n/string() else ())
};

declare
    %test:assertEquals("c", "a")
function fn-pos:persistent-dom-position-after-sibling-path() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return (
        $dom//b/(if (position() = last()) then @n/string() else ()),
        $dom//b/(if (position() = 1) then @n/string() else ())
    )
};

declare
    %test:assertEquals("b", "c")
function fn-pos:in-memory-dom-position-in-predicate() {
    $fn-pos:nodes//b[position() = 2]/@n/string(),
    $fn-pos:nodes//b[position() = last()]/@n/string()
};

declare
    %test:assertEquals("b", "c")
function fn-pos:persistent-dom-position-in-predicate() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return (
        $dom//b[position() = 2]/@n/string(),
        $dom//b[position() = last()]/@n/string()
    )
};

(: in ./position(), the focus of position() is the context item of the predicate, a sequence of one item :)
declare
    %test:assertEquals("a", "b", "c")
function fn-pos:in-memory-dom-path-in-predicate() {
    $fn-pos:nodes//b[./position() = 1]/@n/string()
};

declare
    %test:assertEquals("a", "b", "c")
function fn-pos:persistent-dom-path-in-predicate() {
    let $dom := doc('/db/' || $fn-pos:collection || '/' || $fn-pos:doc)

    return $dom//b[./position() = 1]/@n/string()
};
