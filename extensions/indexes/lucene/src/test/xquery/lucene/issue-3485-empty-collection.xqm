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
 : XQSuite regression test for GitHub #3485: ft:query fails on empty collection
 : that has collection.xconf defining Lucene indexes. Expected: no exception;
 : returns empty (nothing found).
 :
 : @see https://github.com/eXist-db/exist/issues/3485
 :)
module namespace i3485 = "http://exist-db.org/xquery/lucene/issue-3485/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(: Lucene index on p elements; collection will have no documents. :)
declare variable $i3485:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

declare variable $i3485:COLLECTION := "i3485";

declare
    %test:setUp
function i3485:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3485:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i3485:COLLECTION),
      xmldb:store("/db/system/config/db/" || $i3485:COLLECTION, "collection.xconf", $i3485:XCONF) )
};

declare
    %test:tearDown
function i3485:tearDown() {
    xmldb:remove("/db/" || $i3485:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i3485:COLLECTION)
};

(: #3485: ft:query on empty collection with Lucene config – must not throw; returns empty. :)
declare
    %test:assertEquals(0)
function i3485:query-empty-collection-no-exception() {
    count(collection("/db/" || $i3485:COLLECTION)//p[ft:query(., "hello")])
};

(: #3485: ft:search on empty collection – must not throw; returns empty search result. :)
declare
    %test:assertEquals("")
function i3485:search-empty-collection-no-exception() {
    string-join(ft:search("/db/" || $i3485:COLLECTION || "/", "hello")//@uri/data(), " ")
};
