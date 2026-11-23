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
 : A query that reads the focus, the context item, its position or the size of the context, is evaluated for each node of the step with
 : that node's focus. The optimizer pre-selects with the query evaluated once over the documents, before there is a node to compute it
 : from, so it leaves such a call as written. A query that does not read the focus is still looked up.
 : See https://github.com/eXist-db/exist/issues/6956
 :
 : Two el elements hold the same text, "alpha"; their q attributes are "alpha" and "beta". A query computed from the element finds the
 : text of the first only.
 :)
module namespace lkf="http://exist-db.org/xquery/lucene/key-reads-focus/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";
declare namespace ft="http://exist-db.org/xquery/lucene";

declare variable $lkf:COLLECTION_NAME := "test-lucene-key-reads-focus";
declare variable $lkf:COLLECTION := "/db/" || $lkf:COLLECTION_NAME;
declare variable $lkf:CONF := "/db/system/config/db/" || $lkf:COLLECTION_NAME;

declare variable $lkf:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="el"/>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function lkf:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $lkf:COLLECTION_NAME),
    xmldb:store($lkf:CONF, "collection.xconf", $lkf:XCONF),
    xmldb:create-collection("/db", $lkf:COLLECTION_NAME),
    xmldb:store($lkf:COLLECTION, "d.xml", <doc><el q="alpha">alpha</el><el q="beta">alpha</el></doc>)
};

declare
    %test:tearDown
function lkf:tearDown() {
    xmldb:remove($lkf:COLLECTION),
    xmldb:remove($lkf:CONF)
};

declare
    %test:assertEquals(1)
function lkf:query-computed-from-the-node() {
    count(collection($lkf:COLLECTION)//el[ft:query(., string(@q))])
};

declare
    %test:assertEquals(1)
function lkf:query-computed-from-the-node-of-a-variable() {
    let $els := collection($lkf:COLLECTION)//el
    return count($els[ft:query(., string(@q))])
};

(: last() is 2 for both el, so the query is "beta" for both and neither has that text; a query that saw a size of 1 would find both :)
declare
    %test:assertEquals(0)
function lkf:query-uses-last() {
    count(collection($lkf:COLLECTION)//el[ft:query(., (if (last() = 2) then 'beta' else 'alpha'))])
};

(: position() is 1 for the first el only :)
declare
    %test:assertEquals(1)
function lkf:query-uses-position() {
    count(collection($lkf:COLLECTION)//el[ft:query(., (if (position() = 1) then 'alpha' else 'beta'))])
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'])")
function lkf:literal-query-uses-the-index() {
    collection($lkf:COLLECTION)//el[ft:query(., 'alpha')]
};
