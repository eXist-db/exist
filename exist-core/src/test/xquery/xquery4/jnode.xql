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
 : XQSuite tests for XQuery 4.0 JNode support.
 :)
module namespace jn-test = "http://exist-db.org/xquery/test/jnode";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: ==================== fn:jtree ==================== :)

declare
    %test:assertTrue
function jn-test:jtree-map-returns-jnode() {
    let $jnode := fn:jtree(map { "a": 1 })
    return exists($jnode)
};

declare
    %test:assertTrue
function jn-test:jtree-array-returns-jnode() {
    let $jnode := fn:jtree(array { 1, 2, 3 })
    return exists($jnode)
};

declare
    %test:assertTrue
function jn-test:jtree-string-returns-jnode() {
    let $jnode := fn:jtree("hello")
    return exists($jnode)
};

(: ==================== fn:jvalue ==================== :)

declare
    %test:assertEquals("Alice")
function jn-test:jvalue-map-lookup() {
    let $jnode := fn:jtree(map { "name": "Alice" })
    let $val := fn:jvalue($jnode)
    return $val("name")
};

declare
    %test:assertEquals(3)
function jn-test:jvalue-array-size() {
    let $jnode := fn:jtree(array { 1, 2, 3 })
    let $val := fn:jvalue($jnode)
    return array:size($val)
};

declare
    %test:assertEquals("hello")
function jn-test:jvalue-string() {
    let $jnode := fn:jtree("hello")
    return fn:jvalue($jnode)
};

(: ==================== fn:jkey ==================== :)

declare
    %test:assertEmpty
function jn-test:jkey-root-is-empty() {
    let $jnode := fn:jtree(map { "name": "Alice" })
    return fn:jkey($jnode)
};

(: ==================== fn:jposition ==================== :)

declare
    %test:assertEquals(0)
function jn-test:jposition-root-is-zero() {
    let $jnode := fn:jtree(map { "name": "Alice" })
    return fn:jposition($jnode)
};

(: ==================== Round-trip ==================== :)

declare
    %test:assertTrue
function jn-test:jtree-preserves-map() {
    let $original := map { "x": 1, "y": 2 }
    let $jnode := fn:jtree($original)
    let $val := fn:jvalue($jnode)
    return $val instance of map(*)
};

declare
    %test:assertTrue
function jn-test:jtree-preserves-array() {
    let $original := array { "a", "b", "c" }
    let $jnode := fn:jtree($original)
    let $val := fn:jvalue($jnode)
    return $val instance of array(*)
};

(: ==================== Map/Array Predicate Tests ==================== :)

declare
    %test:assertEquals(1)
function jn-test:map-navigation-with-predicate() {
    map{"asdf":1}/asdf[. <= 1]
};

declare
    %test:assertEquals(2)
function jn-test:map-wildcard-with-predicate() {
    map{"a":1,"b":2}/*[. > 1]
};

declare
    %test:assertEquals(2, 3)
function jn-test:array-wildcard-with-predicate() {
    [1,2,3]/*[. > 1]
};

declare
    %test:assertEquals(1)
function jn-test:parenthesized-map-with-predicate() {
    (map{"asdf":1}/asdf)[. <= 1]
};

declare
    %test:assertEquals(1)
function jn-test:nested-map-with-predicate() {
    map{"x": map{"y": 1}}/x/y[. = 1]
};

declare
    %test:assertEquals(2, 3)
function jn-test:map-sequence-value-with-predicate() {
    map{"a": (1,2,3)}/a[. > 1]
};
