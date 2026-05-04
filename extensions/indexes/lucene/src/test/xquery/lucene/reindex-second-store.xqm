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
(:
 : Minimal targeted test for "second store not visible to reindex" bug.
 : No vectors—uses plain Lucene ft:query. Run via ReindexSecondStoreTest for fast debugging.
 :
 : If this fails on develop, the bug is in exist-core (store/collectionsDb), not vector-specific.
 :)
xquery version "3.1";

module namespace rss = "http://exist-db.org/xquery/lucene/reindex-second-store/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $rss:COLLECTION_NAME := "lucene-test-reindex-second-store";
declare variable $rss:COLLECTION := "/db/" || $rss:COLLECTION_NAME;

declare variable $rss:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="doc">
                    <field name="content" expression="string(.)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $rss:DOC_A := <doc><content>alpha</content></doc>;
declare variable $rss:DOC_B := <doc><content>beta</content></doc>;
declare variable $rss:DOC_C := <doc><content>gamma</content></doc>;
declare variable $rss:DOC_D := <doc><content>delta</content></doc>;

declare
    %test:setUp
function rss:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $rss:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $rss:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $rss:COLLECTION_NAME, "collection.xconf", $rss:XCONF),
      xmldb:store($rss:COLLECTION, "a.xml", $rss:DOC_A),
      xmldb:store($rss:COLLECTION, "b.xml", $rss:DOC_B),
      xmldb:reindex($rss:COLLECTION) )
};

declare
    %test:tearDown
function rss:tearDown() {
    ( xmldb:remove($rss:COLLECTION),
      xmldb:remove("/db/system/config/db/" || $rss:COLLECTION_NAME) )
};

(:~ First store + reindex: works. Only c.xml contains "gamma", so expect 1. :)
declare
    %test:assertEquals(1)
function rss:first-store-reindex-works() {
    let $_ := xmldb:store($rss:COLLECTION, "c.xml", $rss:DOC_C)
    let $_ := xmldb:reindex($rss:COLLECTION)
    return count(collection($rss:COLLECTION)//doc[ft:query(., "gamma")])
};

(:~ Second store + reindex: fails if bug present—reindex sees 3 docs not 4, so delta not indexed. :)
declare
    %test:assertEquals(2)
function rss:second-store-reindex-sees-both() {
    let $_ := xmldb:store($rss:COLLECTION, "c.xml", $rss:DOC_C)
    let $_ := xmldb:reindex($rss:COLLECTION)
    let $_ := xmldb:store($rss:COLLECTION, "d.xml", $rss:DOC_D)
    let $_ := xmldb:reindex($rss:COLLECTION)
    return count(collection($rss:COLLECTION)//doc[ft:query(., "gamma")]) + count(collection($rss:COLLECTION)//doc[ft:query(., "delta")])
};

(:~ Store both then reindex once: fails if second store not visible. :)
declare
    %test:assertEquals(2)
function rss:store-both-then-reindex-once() {
    let $_ := xmldb:store($rss:COLLECTION, "c.xml", $rss:DOC_C)
    let $_ := xmldb:store($rss:COLLECTION, "d.xml", $rss:DOC_D)
    let $_ := xmldb:reindex($rss:COLLECTION)
    return count(collection($rss:COLLECTION)//doc[ft:query(., "gamma")]) + count(collection($rss:COLLECTION)//doc[ft:query(., "delta")])
};
