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
 : XQSuite regression test for GitHub #4805: util:index-keys#5 returns stale
 : data when term frequency changes via update insert into (no reindex).
 : After inserting a duplicate, frequency should be 2 but may return 1.
 :
 : Tests share state; setUp runs once per module. Order matters.
 :
 : @see https://github.com/eXist-db/exist/issues/4805
 :)
module namespace i4805 = "http://exist-db.org/xquery/range/issue-4805/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $i4805:XML := document {
    <root>
        <child s="1"/>
    </root>
};

declare variable $i4805:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="@s" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $i4805:col := 'i4805-util-index-keys';

declare function i4805:term($term as xs:string, $data as xs:int+) as item()+ {
    $term, $data
};

declare function i4805:get-nodeset() {
    collection('/db/' || $i4805:col)//@s
};

declare function i4805:list-terms($nodes as node()*, $number-of-results as xs:integer) as item()* {
    util:index-keys($nodes, (), i4805:term#2, $number-of-results, 'range-index')
};

declare
    %test:setUp
function i4805:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db/system/config/db", $i4805:col),
      xmldb:create-collection("/db", $i4805:col),
      xmldb:store("/db/" || $i4805:col, "test.xml", $i4805:XML),
      xmldb:store("/db/system/config/db/" || $i4805:col, "collection.xconf", $i4805:xconf),
      xmldb:reindex("/db/" || $i4805:col) )
};

declare
    %test:tearDown
function i4805:tearDown() {
    xmldb:remove("/db/" || $i4805:col),
    xmldb:remove("/db/system/config/db/" || $i4805:col)
};

declare
    %test:assertEquals('1', 1, 1, 1)
function i4805:test-initial() {
    i4805:list-terms(i4805:get-nodeset(), 1)
};

(: After update insert: freq should be 2; fixed in #4805. Same pattern as updates.xql/range.xql. :)
declare
    %test:assertEquals('1', 2, 1, 1)
function i4805:test-list-after-update() {
    update insert <child s="1"/> into doc('/db/' || $i4805:col || '/test.xml')/root,
    i4805:list-terms(i4805:get-nodeset(), 1)
};

(: Same data as test-list-after-update (2 children); verify max=2 returns correct freq. :)
declare
    %test:assertEquals('1', 2, 1, 1)
function i4805:test-list-with-different-page-size() {
    i4805:list-terms(i4805:get-nodeset(), 2)
};

(: xmldb:reindex returns doc count; we need only list-terms for assertion. :)
declare
    %test:assertEquals('1', 2, 1, 1)
function i4805:test-updated-after-reindex() {
    let $_ := xmldb:reindex('/db/' || $i4805:col)
    return i4805:list-terms(i4805:get-nodeset(), 1)
};
