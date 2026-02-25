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
 : XQSuite regression test for GitHub #2781: Diacritics and truncation with
 : GermanAnalyzer or WhitespaceAnalyzer. Röntgen* and Hand* previously failed
 : with German/Whitespace; StandardAnalyzer worked.
 :
 : Adds to suite: analyzers-index has nun* on l_ws (Whitespace) with query-analyzer-id,
 : but no diacritics in prefix and no ft:query-field on German/Whitespace-indexed
 : fields. facets has to:(ba* müller) with NoDiacriticsStandardAnalyzer; analyzers
 : has prefix+rüssels with ft:query. This file uniquely covers ft:query-field with
 : prefix containing diacritics (Röntgen*) and ASCII prefix (Hand*) on fields
 : indexed by GermanAnalyzer and WhitespaceAnalyzer.
 :
 : @see https://github.com/eXist-db/exist/issues/2781
 :)
module namespace i2781="http://exist-db.org/xquery/lucene/issue-2781/test";

declare namespace f="http://www.faustedition.net/ns";
declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Collection config: default (Standard), german, ws fields on f:p. :)
declare variable $i2781:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:f="http://www.faustedition.net/ns">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <analyzer id="german" class="org.apache.lucene.analysis.de.GermanAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text field="default" qname="f:p"/>
                <text field="german" qname="f:p" analyzer="german"/>
                <text field="ws" qname="f:p" analyzer="ws"/>
            </lucene>
        </index>
    </collection>;

(:~ Test document from #2781: Röntgenbilder der Handschriften angefertigt. :)
declare variable $i2781:XML as document-node() :=
    document {
        <f:doc>
            <f:p>Röntgenbilder der Handschriften angefertigt.</f:p>
        </f:doc>
    };

declare variable $i2781:COLLECTION_NAME := "lucene-test-issue-2781";
declare variable $i2781:COLLECTION := "/db/" || $i2781:COLLECTION_NAME;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function i2781:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i2781:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i2781:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i2781:COLLECTION_NAME, "collection.xconf", $i2781:XCONF),
      xmldb:store($i2781:COLLECTION, "test.xml", $i2781:XML),
      xmldb:reindex($i2781:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function i2781:tearDown() {
    xmldb:remove($i2781:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i2781:COLLECTION_NAME)
};

(:~ #2781: truncation with diacritics - StandardAnalyzer (default) works. :)
declare %test:assertExists function i2781:diacritics-trunc-default() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("default", "Röntgen*")]
};

(:~ #2781: truncation with diacritics - GermanAnalyzer (reported failing). :)
declare %test:assertExists function i2781:diacritics-trunc-german() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("german", "Röntgen*")]
};

(:~ #2781: truncation with diacritics - WhitespaceAnalyzer (reported failing). :)
declare %test:assertExists function i2781:diacritics-trunc-ws() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("ws", "Röntgen*")]
};

(:~ Exact match without truncation - default. :)
declare %test:assertExists function i2781:diacritics-exact-default() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("default", "Röntgenbilder")]
};

(:~ Exact match without truncation - GermanAnalyzer. :)
declare %test:assertExists function i2781:diacritics-exact-german() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("german", "Röntgenbilder")]
};

(:~ Exact match without truncation - WhitespaceAnalyzer. :)
declare %test:assertExists function i2781:diacritics-exact-ws() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("ws", "Röntgenbilder")]
};

(:~ #2781: ASCII truncation - default. :)
declare %test:assertExists function i2781:ascii-trunc-default() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("default", "Hand*")]
};

(:~ #2781: ASCII truncation - GermanAnalyzer. :)
declare %test:assertExists function i2781:ascii-trunc-german() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("german", "Hand*")]
};

(:~ #2781: ASCII truncation - WhitespaceAnalyzer (reported failing). :)
declare %test:assertExists function i2781:ascii-trunc-ws() {
    collection($i2781:COLLECTION)//f:p[ft:query-field("ws", "Hand*")]
};
