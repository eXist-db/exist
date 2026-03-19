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
 : XQSuite tests for ft:query/ft:search edge cases.
 : Merges issue-3485 (empty collection), issue-2948 (no index / empty index).
 :
 : @see https://github.com/eXist-db/exist/issues/3485
 : @see https://github.com/eXist-db/exist/issues/2948
 :)
module namespace fte="http://exist-db.org/xquery/lucene/ft-edge/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: #3485: Lucene index on p; collection has no documents. :)
declare variable $fte:XCONF_EMPTY as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene><text qname="p"/></lucene>
        </index>
    </collection>;

(: #2948: No index element. :)
declare variable $fte:XCONF_NO_INDEX as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <triggers/>
    </collection>;

(: #2948: Empty index element. :)
declare variable $fte:XCONF_EMPTY_INDEX as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
    </collection>;

declare variable $fte:DATA as document-node() := document { <doc><p>hello world</p></doc> };

declare variable $fte:COLL_EMPTY := "lucene-test-ft-edge-empty";
declare variable $fte:COLL_NO_INDEX := "lucene-test-ft-edge-no-index";
declare variable $fte:COLL_EMPTY_INDEX := "lucene-test-ft-edge-empty-index";

declare
    %test:setUp
function fte:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $fte:COLL_EMPTY),
      xmldb:create-collection("/db/system/config/db", $fte:COLL_EMPTY),
      xmldb:store("/db/system/config/db/" || $fte:COLL_EMPTY, "collection.xconf", $fte:XCONF_EMPTY),
      xmldb:create-collection("/db", $fte:COLL_NO_INDEX),
      xmldb:create-collection("/db/system/config/db", $fte:COLL_NO_INDEX),
      xmldb:store("/db/system/config/db/" || $fte:COLL_NO_INDEX, "collection.xconf", $fte:XCONF_NO_INDEX),
      xmldb:store("/db/" || $fte:COLL_NO_INDEX, "test.xml", $fte:DATA),
      xmldb:create-collection("/db", $fte:COLL_EMPTY_INDEX),
      xmldb:create-collection("/db/system/config/db", $fte:COLL_EMPTY_INDEX),
      xmldb:store("/db/system/config/db/" || $fte:COLL_EMPTY_INDEX, "collection.xconf", $fte:XCONF_EMPTY_INDEX),
      xmldb:store("/db/" || $fte:COLL_EMPTY_INDEX, "test.xml", $fte:DATA) )
};

declare
    %test:tearDown
function fte:tearDown() {
    ( xmldb:remove("/db/" || $fte:COLL_EMPTY),
      xmldb:remove("/db/system/config/db/" || $fte:COLL_EMPTY),
      xmldb:remove("/db/" || $fte:COLL_NO_INDEX),
      xmldb:remove("/db/system/config/db/" || $fte:COLL_NO_INDEX),
      xmldb:remove("/db/" || $fte:COLL_EMPTY_INDEX),
      xmldb:remove("/db/system/config/db/" || $fte:COLL_EMPTY_INDEX) )
};

(: #3485: ft:query on empty collection with Lucene config – must not throw; returns empty. :)
declare %test:assertEquals(0) function fte:query-empty-collection-no-exception() {
    count(collection("/db/" || $fte:COLL_EMPTY)//p[ft:query(., "hello")])
};

(: #3485: ft:search on empty collection – must not throw; returns empty. :)
declare %test:assertEquals("") function fte:search-empty-collection-no-exception() {
    string-join(ft:search("/db/" || $fte:COLL_EMPTY || "/", "hello")//@uri/data(), " ")
};

(: #2948: ft:query on collection with no index element – must not NPE; returns empty. :)
declare %test:assertEquals(0) function fte:query-no-index-element-no-npe() {
    count(collection("/db/" || $fte:COLL_NO_INDEX)//p[ft:query(., "hello")])
};

(: #2948: ft:query on collection with empty index element – must not NPE; returns empty. :)
declare %test:assertEquals(0) function fte:query-empty-index-element-no-npe() {
    count(collection("/db/" || $fte:COLL_EMPTY_INDEX)//p[ft:query(., "hello")])
};
