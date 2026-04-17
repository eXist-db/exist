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
  Tests for fn:not() predicate handling.
  https://github.com/eXist-db/exist/issues/2159
  https://github.com/eXist-db/exist/issues/2308
~:)
module namespace fn-not="http://exist-db.org/xquery/test/fn-not";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fn-not:collection := 'test-fn-not';
declare variable $fn-not:doc := 'test.xml';
declare variable $fn-not:nodes := document {
    <root>
        <item type="a"><child/></item>
        <item type="b"/>
        <item type="a"/>
    </root>
};

declare
    %test:setUp
function fn-not:setup() {
    xmldb:create-collection('/db', $fn-not:collection),
    xmldb:store('/db/' || $fn-not:collection, $fn-not:doc, $fn-not:nodes)
};

declare
    %test:tearDown
function fn-not:teardown() {
    xmldb:remove('/db/' || $fn-not:collection)
};

(: #2159 — not(self::x) on empty derived path must not crash :)
declare
    %test:assertEquals(0)
function fn-not:empty-path-not-self() {
    let $doc := <root><abc/></root>
    return count($doc/nonexistent/*[not(self::abc)])
};

(: #2159 — not(self::x) on non-empty path filters correctly :)
declare
    %test:assertEquals(1)
function fn-not:non-empty-path-not-self() {
    let $doc := <root><abc/><def/></root>
    return count($doc/*[not(self::abc)])
};

(: #2159 — not(*) on empty path returns empty :)
declare
    %test:assertEquals(0)
function fn-not:empty-path-not-wildcard() {
    let $doc := <root><abc/></root>
    return count($doc/nonexistent/*[not(*)])
};

(: Standalone not(()) returns true — boolean path unaffected :)
declare
    %test:assertTrue
function fn-not:standalone-not-empty() {
    not(())
};

(: Set-difference optimization on persistent nodes — not(child) :)
declare
    %test:assertEquals(2)
function fn-not:persistent-not-child() {
    let $dom := doc('/db/' || $fn-not:collection || '/' || $fn-not:doc)
    return count($dom//item[not(child)])
};

(: Set-difference optimization on persistent nodes — not(self::x) :)
declare
    %test:assertEquals(1)
function fn-not:persistent-not-self() {
    let $dom := doc('/db/' || $fn-not:collection || '/' || $fn-not:doc)
    return count($dom//item[not(@type = 'a')])
};

(: #2308 — not(.) on integer sequence :)
declare
    %test:assertEquals(1)
function fn-not:not-dot-integers() {
    count((0, 1, 2)[not(.)])
};

(: #2308 — not(.) on string sequence :)
declare
    %test:assertEquals(1)
function fn-not:not-dot-strings() {
    count(("", "a")[not(.)])
};

(: #2308 — not(.) on mixed booleans :)
declare
    %test:assertEquals(1)
function fn-not:not-dot-booleans() {
    count((true(), false())[not(.)])
};

(: not(.) on in-memory node sequence filters correctly :)
declare
    %test:assertEquals(0)
function fn-not:not-dot-nodes() {
    count((<a/>, <b/>)[not(.)])
};
