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
 : tests focussing on filtering function items in context
 :)
module namespace filter-function-items-in-context="http://exist-db.org/xquery/test/filter-function-items-in-context";


declare namespace test="http://exist-db.org/xquery/xqsuite";


declare variable $filter-function-items-in-context:maps := ( map { "id": 1 }, map { "id": 2, "foo": 3 } );
declare variable $filter-function-items-in-context:arrays := ( [1, 2], ["a", "b", "c"] );

declare
    %test:assertEquals(1)
function filter-function-items-in-context:map-function() {
    $filter-function-items-in-context:maps[map:size(.) eq 1]?id
};

declare
    %test:assertEquals(2)
function filter-function-items-in-context:map-get() {
    $filter-function-items-in-context:maps[map:get(., "id") eq 2]?id
};

declare
    %test:assertEquals(2)
function filter-function-items-in-context:map-key() {
    $filter-function-items-in-context:maps[(.)("id") eq 2]?id
};

declare
    %test:assertEquals(2)
function filter-function-items-in-context:map-lookup() {
    $filter-function-items-in-context:maps[(.)?id eq 2]?id
};

declare
    %test:assertEquals("a", "b", "c")
function filter-function-items-in-context:array-get() {
    $filter-function-items-in-context:arrays[array:get(.,2) instance of xs:string]?*
};

declare
    %test:assertEquals("a", "b", "c")
function filter-function-items-in-context:array-lookup() {
    $filter-function-items-in-context:arrays[(.)?2 instance of xs:string]?*
};

declare
    %test:assertEquals(1)
function filter-function-items-in-context:array-function() {
    $filter-function-items-in-context:arrays[array:size(.) eq 2]?1
};

declare
    %test:assertEquals(1)
function filter-function-items-in-context:named-function-arity () {
    (
        current-date#0,
        string-join#1
    )
    [function-arity(.) eq 1] => count()
};

declare
    %test:assertEquals(1)
function filter-function-items-in-context:anonymous-function-arity () {
    (
        function() { "foo" },
        function($x) { "bar" }
    )
    [function-arity(.) eq 1] => count()
};

declare
    %test:assertEquals(7)
function filter-function-items-in-context:mixed-sequence () {
    (
        $filter-function-items-in-context:arrays,
        $filter-function-items-in-context:maps,
        string-join#1,
        function($x) { $x },
        sum#1
    )
    [. instance of function(*)] => count()
};
