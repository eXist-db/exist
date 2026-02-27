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
 : XQSuite regression test for GitHub #4455: facet merging for consecutive
 : ft:query calls on the same nodes produces inflated counts. When chained
 : predicates filter the same nodes (e.g. //doc[ft:query(., "a")][ft:query(., "b")]),
 : facet counts should reflect the final result set (1 doc), not the sum from
 : each query (2 + 1 = 3). Merging is correct for union of different node types.
 :
 : @see https://github.com/eXist-db/exist/issues/4455
 :)
module namespace i4455 = "http://exist-db.org/xquery/lucene/issue-4455/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i4455:COLLECTION := "i4455";

declare variable $i4455:DOCUMENTS as element(documents) :=
    <documents>
        <document id="D-37/2">
            <title>Ruhe im Wald</title>
            <abstract>Es zwitschern die Vögel im Walde</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="D-37/2">
            <title>Birne im Wald</title>
            <abstract>Es zwitschern die Vögel im Walde</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="Z-49/2">
            <title>Streiten und Hoffen</title>
            <abstract>Da nun einmal der Himmel zerrissen und die Götter sich streiten</abstract>
            <category>philosophy</category>
        </document>
    </documents>;

declare variable $i4455:IMAGES as element(images) :=
    <images>
        <image>
            <title>Wanderer im Wald</title>
            <category>nature</category>
        </image>
        <image>
            <title>Stilleben mit Birne</title>
            <category>food</category>
        </image>
    </images>;

declare variable $i4455:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.de.GermanAnalyzer" id="german"/>
                <text qname="document">
                    <facet dimension="cat" expression="category"/>
                    <field name="title" expression="title"/>
                    <field name="abstract" expression="abstract" analyzer="german"/>
                </text>
                <text qname="image">
                    <facet dimension="cat" expression="category"/>
                    <field name="title" expression="title"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function i4455:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4455:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4455:COLLECTION),
      xmldb:store("/db/" || $i4455:COLLECTION, "documents.xml", $i4455:DOCUMENTS),
      xmldb:store("/db/" || $i4455:COLLECTION, "images.xml", $i4455:IMAGES),
      xmldb:store("/db/system/config/db/" || $i4455:COLLECTION, "collection.xconf", $i4455:XCONF),
      xmldb:reindex("/db/" || $i4455:COLLECTION) )
};

declare
    %test:tearDown
function i4455:tearDown() {
    xmldb:remove("/db/" || $i4455:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4455:COLLECTION)
};

(: Chained predicates on same nodes: vögel -> 2 docs, then title:Ruhe -> 1 doc.
   Facet count for nature must be 1, not 2+1=3. FIXME: currently merges all queries
   and returns 3; fix requires distinguishing chained predicates from union-merged matches. :)
declare
    %test:pending("GitHub #4455 – facet merge for chained ft:query not yet implemented")
    %test:assertEquals(1)
function i4455:facets-same-nodes-chained-predicates() {
    let $results := doc("/db/" || $i4455:COLLECTION || "/documents.xml")//document[ft:query(., "vögel")][ft:query(., "title:Ruhe")]
    return ft:facets($results, "cat")?nature
};

(: Union of document and image: each node has one LuceneMatch; merging sums correctly.
   Documents matching "wald": 2 (both nature); image matching "wald": 1 (nature) → 3. :)
declare
    %test:assertEquals(3)
function i4455:facets-different-nodes-union() {
    let $results := collection("/db/" || $i4455:COLLECTION)//(document[ft:query(., "wald")] | image[ft:query(., "wald")])
    return ft:facets($results, "cat")?nature
};
