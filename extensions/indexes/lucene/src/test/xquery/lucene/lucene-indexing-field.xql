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
 : Isolated XQSuite tests for Lucene field indexing.
 :
 : Verifies that Lucene indexes and queries field values (place, from, to) correctly.
 : Uses a minimal, self-contained setup with no dependency on facets, taxonomy,
 : or shared collections.
 :)

module namespace lif="http://exist-db.org/xquery/lucene/test/indexing-field";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

(:~
 : Minimal test data: three letters with place and from fields.
 : @return document-node()
 :)
declare variable $lif:XML as document-node() := document {
    <letters>
        <letter><from>Hans</from><to>Egon</to><place>Berlin</place></letter>
        <letter><from>Rudi</from><to>Egon</to><place>Berlin</place></letter>
        <letter><from>Susi</from><to>Hans</to><place>Hamburg</place></letter>
    </letters>
};

(:~
 : Collection config: Lucene index on letter with fields place, from, to.
 : No facets, no modules – minimal indexing config.
 : @return element(collection)
 :)
declare variable $lif:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer" id="nodiacritics"/>
                <text qname="letter" analyzer="nodiacritics">
                    <field name="place" expression="place" analyzer="nodiacritics"/>
                    <field name="from" expression="from" store="no"/>
                    <field name="to" expression="to"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Test collection name (no path). :)
declare variable $lif:COLLECTION_NAME := "lucene-indexing-field";

(:~ Full path of the test collection. :)
declare variable $lif:COLLECTION := "/db/" || $lif:COLLECTION_NAME;

(:~
 : XQSuite setUp: create config parent chain, test collection, config subcollection,
 : store xconf and document, reindex.
 :)
declare
    %test:setUp
function lif:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $lif:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $lif:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $lif:COLLECTION_NAME, "collection.xconf", $lif:xconf),
      xmldb:store($lif:COLLECTION, "test.xml", $lif:XML),
      xmldb:reindex($lif:COLLECTION) )
};

(:~
 : XQSuite tearDown: remove test collection and its config.
 :)
declare
    %test:tearDown
function lif:tearDown() {
    xmldb:remove($lif:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $lif:COLLECTION_NAME)
};

(:~
 : place:hamburg should return 1 hit.
 : @return xs:integer
 :)
declare
    %test:assertEquals(1)
function lif:query-field-place-hamburg() {
    count(collection($lif:COLLECTION)//letter[ft:query(., "place:hamburg")])
};

(:~
 : place:berlin should return 2 hits.
 : @return xs:integer
 :)
declare
    %test:assertEquals(2)
function lif:query-field-place-berlin() {
    count(collection($lif:COLLECTION)//letter[ft:query(., "place:berlin")])
};

(:~
 : from:rudi AND place:berlin should return 1 hit.
 : @return xs:integer
 :)
declare
    %test:assertEquals(1)
function lif:query-field-from-and-place() {
    count(collection($lif:COLLECTION)//letter[ft:query(., "from:rudi AND place:berlin")])
};

(:~
 : from:susi AND place:berlin should return 0 (Susi is in Hamburg).
 : @return xs:integer
 :)
declare
    %test:assertEquals(0)
function lif:query-field-mismatch() {
    count(collection($lif:COLLECTION)//letter[ft:query(., "from:susi AND place:berlin")])
};

(:~
 : Main content search: "Egon" appears in 2 letters (Hans->Egon, Rudi->Egon).
 : @return xs:integer
 :)
declare
    %test:assertEquals(2)
function lif:query-main-content() {
    count(collection($lif:COLLECTION)//letter[ft:query(., "Egon")])
};
