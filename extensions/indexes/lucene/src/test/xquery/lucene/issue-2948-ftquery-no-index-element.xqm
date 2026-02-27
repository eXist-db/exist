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
 : XQSuite regression test for GitHub #2948: ft:query throws NPE when
 : collection.xconf lacks the index element or has empty index. Expected:
 : no NPE; query returns empty (no Lucene index on collection).
 :
 : @see https://github.com/eXist-db/exist/issues/2948
 :)
module namespace i2948 = "http://exist-db.org/xquery/lucene/issue-2948/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i2948:DATA as document-node() := document { <doc><p>hello world</p></doc> };

(: Case A: No index element – only triggers (empty). :)
declare variable $i2948:XCONF_NO_INDEX as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <triggers/>
    </collection>;

(: Case B: Empty index element. :)
declare variable $i2948:XCONF_EMPTY_INDEX as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
    </collection>;

declare variable $i2948:COLL_NO_INDEX := "i2948-no-index";
declare variable $i2948:COLL_EMPTY_INDEX := "i2948-empty-index";

declare
    %test:setUp
function i2948:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i2948:COLL_NO_INDEX),
      xmldb:create-collection("/db/system/config/db", $i2948:COLL_NO_INDEX),
      xmldb:store("/db/system/config/db/" || $i2948:COLL_NO_INDEX, "collection.xconf", $i2948:XCONF_NO_INDEX),
      xmldb:store("/db/" || $i2948:COLL_NO_INDEX, "test.xml", $i2948:DATA),
      xmldb:create-collection("/db", $i2948:COLL_EMPTY_INDEX),
      xmldb:create-collection("/db/system/config/db", $i2948:COLL_EMPTY_INDEX),
      xmldb:store("/db/system/config/db/" || $i2948:COLL_EMPTY_INDEX, "collection.xconf", $i2948:XCONF_EMPTY_INDEX),
      xmldb:store("/db/" || $i2948:COLL_EMPTY_INDEX, "test.xml", $i2948:DATA) )
};

declare
    %test:tearDown
function i2948:tearDown() {
    xmldb:remove("/db/" || $i2948:COLL_NO_INDEX),
    xmldb:remove("/db/system/config/db/" || $i2948:COLL_NO_INDEX),
    xmldb:remove("/db/" || $i2948:COLL_EMPTY_INDEX),
    xmldb:remove("/db/system/config/db/" || $i2948:COLL_EMPTY_INDEX)
};

(: #2948: ft:query on collection with no index element – must not NPE; returns empty. :)
declare
    %test:assertEquals(0)
function i2948:query-no-index-element-no-npe() {
    count(collection("/db/" || $i2948:COLL_NO_INDEX)//p[ft:query(., "hello")])
};

(: #2948: ft:query on collection with empty index element – must not NPE; returns empty. :)
declare
    %test:assertEquals(0)
function i2948:query-empty-index-element-no-npe() {
    count(collection("/db/" || $i2948:COLL_EMPTY_INDEX)//p[ft:query(., "hello")])
};
