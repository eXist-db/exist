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
 : A predicate is evaluated once per item of its context, with that item as the focus
 : (XPath 3.1 §3.3.3). A for or let clause in it keeps that focus, and a predicate value that
 : is a single number selects the item only if it equals the item's own position.
 : See https://github.com/eXist-db/exist/issues/6832.
 :)
module namespace pf="http://exist-db.org/xquery/test/predicate-focus";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $pf:COLL := "/db/test-predicate-focus";

declare
    %test:setUp
function pf:setup() {
    xmldb:create-collection("/db", "test-predicate-focus"),
    for $name in ("a", "b", "c")
    return xmldb:store($pf:COLL, $name || ".xml", <article><name>{$name}</name></article>)
};

declare
    %test:tearDown
function pf:teardown() {
    xmldb:remove($pf:COLL)
};

declare
    %test:assertEquals(3)
function pf:for-in-predicate-stored() {
    count(collection($pf:COLL)//article[exists(for $i in 1 to 2 return .)])
};

declare
    %test:assertEquals(2)
function pf:for-in-predicate-in-memory() {
    count((<a/>, <b/>)[exists(for $i in 1 to 2 return .)])
};

declare
    %test:assertEquals("a", "b", "c")
function pf:for-return-clause-sees-candidate() {
    collection($pf:COLL)//article[for $i in 1 return name = name]/name/string()
};

declare
    %test:assertEquals(2)
function pf:let-bound-to-context-item() {
    (3, 2, 1)[let $x := . return $x]
};

declare
    %test:assertEquals(3)
function pf:let-in-predicate-stored() {
    count(collection($pf:COLL)//article[let $n := string(name) return $n = name])
};

declare
    %test:assertEquals(2)
function pf:numeric-value-is-compared-to-position() {
    (3, 2, 1)[.]
};

declare
    %test:assertEquals(1)
function pf:numeric-value-selects-only-its-own-position() {
    count((2, 2, 2)[.])
};

declare
    %test:assertEquals("3")
function pf:numeric-attribute-value() {
    (<r><a n="2"/><a n="1"/><a n="3"/></r>/a)[xs:integer(@n)]/@n/string()
};

(: on a reverse axis, positions count from the context node backwards: b is 1, a is 2 :)
declare
    %test:assertEmpty
function pf:numeric-value-on-reverse-axis() {
    <r><a n="1"/><b n="2"/><c/></r>/c/preceding-sibling::*[xs:integer(@n)]
};

declare
    %test:assertEquals(6, 6, 7)
function pf:positional-predicates-unchanged() {
    (5, 6, 7)[2], (5, 6, 7)[position() = 2], (5, 6, 7)[last()]
};
