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
 : fn:matches is declared matches($input as xs:string?, ...), so supplying more than one item is a
 : type error. eXist used to test only the first item, so matches(('x','a'), 'a') answered false.
 :
 : What is checked here is the path that evaluates the argument as a value. Inside a predicate on
 : stored nodes, fn:matches still behaves existentially, as an index-backed node filter; the
 : conformant way to write that test is some $v in v satisfies matches($v, 'a').
 :)
module namespace mcard="http://exist-db.org/xquery/test/matches-cardinality";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $mcard:COLLECTION := "/db/matches-cardinality-test";
declare variable $mcard:DOC := $mcard:COLLECTION || "/fixture.xml";

declare
    %test:setUp
function mcard:setup() {
    xmldb:create-collection("/db", "matches-cardinality-test"),
    xmldb:store($mcard:COLLECTION, "fixture.xml",
        <data>
            <entry><val>a</val></entry>
            <entry><val>x</val><val>a</val></entry>
        </data>)
};

declare
    %test:tearDown
function mcard:tear-down() {
    xmldb:remove($mcard:COLLECTION)
};

(:~ The case from the original report: a literal sequence was silently reduced to its first item. :)
declare
    %test:assertError("XPTY0004")
function mcard:a-literal-sequence-is-a-type-error() {
    matches(('x', 'a'), 'a')
};

(:~ The same holds for a multi-item node sequence evaluated as a value. :)
declare
    %test:assertError("XPTY0004")
function mcard:a-multi-item-node-sequence-is-a-type-error() {
    let $v := doc($mcard:DOC)//entry[2]/val
    return matches($v, 'a')
};

declare
    %test:assertTrue
function mcard:a-single-item-is-fine() {
    matches('a', 'a')
};

(:~ The empty sequence is permitted by xs:string? and is not a match. :)
declare
    %test:assertFalse
function mcard:the-empty-sequence-is-fine() {
    matches((), 'a')
};

declare
    %test:assertTrue
function mcard:a-single-node-is-fine() {
    let $v := doc($mcard:DOC)//entry[1]/val
    return matches($v, 'a')
};

(:~ The existential spelling, which the optimizer routes through the index: one item per iteration. :)
declare
    %test:assertEquals(2)
function mcard:the-quantified-spelling-still-works() {
    count(doc($mcard:DOC)//entry[some $v in val satisfies matches($v, 'a')])
};
