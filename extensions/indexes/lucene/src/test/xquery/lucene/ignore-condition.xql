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
 : XQSuite tests for Lucene &lt;ignore&gt; with condition (predicate).
 : Only elements matching the condition should be ignored; others stay indexed.
 :
 : @see https://github.com/eXist-db/exist/issues/1113
 :)
module namespace igc="http://exist-db.org/xquery/lucene/ignore-condition/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: index doc; ignore note only when @type='editorial'. @return element
 :)
declare variable $igc:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="doc">
                    <ignore qname="note" condition="@type='editorial'"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~
 : Test document: author note has "needle", editorial note has "onlyhere". @return document-node
 :)
declare variable $igc:XML as document-node() :=
    document {
        <doc>
            <note type="author">needle</note>
            <note type="editorial">onlyhere</note>
        </doc>
    };

declare variable $igc:COLLECTION_NAME := "ignore-condition-1113";
declare variable $igc:COLLECTION := "/db/" || $igc:COLLECTION_NAME;

(:~
 : setUp: create collection, config with conditional ignore, store doc, reindex.
 : @see https://github.com/eXist-db/exist/issues/1113
 :)
declare
    %test:setUp
function igc:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $igc:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $igc:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $igc:COLLECTION_NAME, "collection.xconf", $igc:XCONF),
      xmldb:store($igc:COLLECTION, "test.xml", $igc:XML),
      xmldb:reindex($igc:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function igc:tearDown() {
    xmldb:remove($igc:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $igc:COLLECTION_NAME),
    xmldb:reindex($igc:COLLECTION)
};

(:~
 : With ignore condition @type='editorial', author note is indexed so "needle" should match once.
 : @param $query full-text query
 : @return count of doc elements matching the query
 : @see https://github.com/eXist-db/exist/issues/1113
 :)
declare
    %test:args("needle")
    %test:pending("ignore with condition not implemented, see #1113")
    %test:assertEquals(1)
function igc:author-note-indexed($query as xs:string) {
    count(collection($igc:COLLECTION)//doc[ft:query(., $query)])
};

(:~
 : With ignore condition @type='editorial', editorial note is not indexed so "onlyhere" should not match.
 : (Currently passes because all note are ignored; after #1113 only editorial would be ignored.)
 : @param $query full-text query
 : @return count of doc elements matching the query
 : @see https://github.com/eXist-db/exist/issues/1113
 :)
declare
    %test:args("onlyhere")
    %test:assertEquals(0)
function igc:editorial-note-ignored($query as xs:string) {
    count(collection($igc:COLLECTION)//doc[ft:query(., $query)])
};
