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
 : XQSuite regression test for GitHub #3042: Wrong sorting when using Lucene
 : fields. order by ft:field($o,"xmlid") gives wrong order when combined with
 : cit[ft:query(quote,...)]/quote (result is child of predicate context).
 : Fix: use ft:query as direct source so returned nodes have matches.
 :
 : FIXME: On develop the whole suite passes (including predicate-pattern tests).
 :
 : @see https://github.com/eXist-db/exist/issues/3042
 :)
module namespace i3042="http://exist-db.org/xquery/lucene/issue-3042/test";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Collection config: cit > quote with field xmlid from @xml:id. :)
declare variable $i3042:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:tei="http://www.tei-c.org/ns/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.core.KeywordAnalyzer" id="keyword"/>
                <text qname="tei:quote" analyzer="keyword">
                    <field name="xmlid" expression="@xml:id/string()" analyzer="keyword"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Test document: cites with quotes, xml:id out of order (z, a) for sort assertion. :)
declare variable $i3042:XML as document-node() :=
    document {
        <tei:body xmlns:tei="http://www.tei-c.org/ns/1.0">
            <tei:cit><tei:quote xml:id="z">term</tei:quote></tei:cit>
            <tei:cit><tei:quote xml:id="a">term</tei:quote></tei:cit>
        </tei:body>
    };

declare variable $i3042:COLLECTION_NAME := "lucene-test-issue-3042";
declare variable $i3042:COLLECTION := "/db/" || $i3042:COLLECTION_NAME;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function i3042:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3042:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i3042:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i3042:COLLECTION_NAME, "collection.xconf", $i3042:XCONF),
      xmldb:store($i3042:COLLECTION, "test.xml", $i3042:XML),
      xmldb:reindex($i3042:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function i3042:tearDown() {
    xmldb:remove($i3042:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i3042:COLLECTION_NAME)
};

(:~ #3042: order by ft:field works when nodes come from ft:query (not predicate). :)
declare %test:assertEquals("a", "z")
function i3042:sort-cit-query-quote-child() {
    let $doc := doc($i3042:COLLECTION || "/test.xml")
    for $q in ft:query($doc//tei:cit/tei:quote, "term")
    order by ft:field($q, "xmlid")
    return $q/@xml:id/string()
};

(:~ Same pattern: ft:query as direct source returns nodes with matches. :)
declare %test:assertEquals("a", "z")
function i3042:sort-quote-query-self() {
    let $doc := doc($i3042:COLLECTION || "/test.xml")
    for $q in ft:query($doc//tei:quote, "term")
    order by ft:field($q, "xmlid")
    return $q/@xml:id/string()
};

(:~ #3042 predicate pattern: quote[ft:query(.,...)]. :)
declare %test:assertEquals("a", "z")
function i3042:sort-quote-predicate-self() {
    let $doc := doc($i3042:COLLECTION || "/test.xml")
    for $q in $doc//tei:quote[ft:query(., "term")]
    order by ft:field($q, "xmlid")
    return $q/@xml:id/string()
};

(:~ #3042 predicate pattern: cit[ft:query(quote)]/quote. :)
declare %test:assertEquals("a", "z")
function i3042:sort-cit-query-quote-predicate() {
    let $doc := doc($i3042:COLLECTION || "/test.xml")
    for $q in $doc//tei:cit[ft:query(tei:quote, "term")]/tei:quote
    order by ft:field($q, "xmlid")
    return $q/@xml:id/string()
};
