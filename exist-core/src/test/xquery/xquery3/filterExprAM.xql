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
 : Tests for XQuery 4.0 FilterExprAM (?[expr]) — array/map filter expression.
 :)
module namespace fam = "http://exist-db.org/xquery/test/filter-expr-am";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: === Array filtering === :)

declare
    %test:assertEquals(2)
function fam:array-filter-size() {
    let $result := [1, 2, 3, 4, 5]?[. > 3]
    return array:size($result)
};

declare
    %test:assertEquals(4, 5)
function fam:array-filter-values() {
    let $result := [1, 2, 3, 4, 5]?[. > 3]
    return ($result(1), $result(2))
};

declare
    %test:assertEquals(0)
function fam:array-filter-none-match() {
    let $result := [1, 2, 3]?[. > 10]
    return array:size($result)
};

declare
    %test:assertEquals(3)
function fam:array-filter-all-match() {
    let $result := [1, 2, 3]?[. > 0]
    return array:size($result)
};

declare
    %test:assertTrue
function fam:array-filter-returns-array() {
    [1, 2, 3]?[. > 1] instance of array(*)
};

declare
    %test:assertEquals(0)
function fam:array-filter-empty() {
    let $result := []?[. > 0]
    return array:size($result)
};

declare
    %test:assertEquals(3)
function fam:array-filter-even() {
    array:size([1, 2, 3, 4, 5, 6]?[. mod 2 = 0])
};

declare
    %test:assertEquals(3)
function fam:array-filter-strings() {
    array:size(["apple", "banana", "avocado", "cherry", "apricot"]?[starts-with(., "a")])
};

(: === Map filtering === :)

declare
    %test:assertTrue
function fam:map-filter-returns-map() {
    map { "a": 1, "b": 2, "c": 3 }?[. > 1] instance of map(*)
};

declare
    %test:assertEquals(2)
function fam:map-filter-size() {
    map:size(map { "a": 1, "b": 2, "c": 3 }?[. > 1])
};

declare
    %test:assertEquals(0)
function fam:map-filter-empty-result() {
    map:size(map { "a": 1, "b": 2 }?[. > 10])
};

declare
    %test:assertEquals(2)
function fam:map-filter-all-entries() {
    map:size(map { "a": 1, "b": 2 }?[. > 0])
};

(: === Chaining === :)

declare
    %test:assertEquals(3)
function fam:chain-filter-then-size() {
    let $data := [10, 20, 30, 40, 50]
    return array:size($data?[. >= 30])
};

(: === Type error === :)

declare
    %test:assertError("XPTY0004")
function fam:type-error-on-string() {
    "hello"?[. > 0]
};

declare
    %test:assertError("XPTY0004")
function fam:type-error-on-integer() {
    42?[. > 0]
};
