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
 : XQSuite regression test for GitHub #3043: Wrong XPath results with Lucene
 : fields. count(head($node/ancestor::tei:entry)/preceding-sibling::tei:entry)+1
 : yields wrong entry position when $node comes from tei:quote[ft:query(.,...)]
 : combined with order by ft:field($o,"xmlid").
 :
 : Workaround (from issue): change to [ft:query(tei:quote,...)]/tei:quote and
 : order by root($o)/*/@xml:id instead of ft:field.
 :
 : Reproducer: https://bitbucket.org/fryske-akademy/online-dictionaries/src/fields_wrong_xpath/readme.md
 :
 : @see https://github.com/eXist-db/exist/issues/3043
 :)
module namespace i3043="http://exist-db.org/xquery/lucene/issue-3043/test";

declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Collection config: quote with field xmlid from @xml:id. :)
declare variable $i3043:XCONF as element(collection) :=
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

(:~ Test document: entry[1]>cit>quote, entry[2]>cit>quote. Entry position = count(preceding-sibling)+1. :)
declare variable $i3043:XML as document-node() :=
    document {
        <tei:body xmlns:tei="http://www.tei-c.org/ns/1.0">
            <tei:entry n="1"><tei:cit><tei:quote xml:id="a">term</tei:quote></tei:cit></tei:entry>
            <tei:entry n="2"><tei:cit><tei:quote xml:id="z">term</tei:quote></tei:cit></tei:entry>
        </tei:body>
    };

declare variable $i3043:COLLECTION_NAME := "lucene-test-issue-3043";
declare variable $i3043:COLLECTION := "/db/" || $i3043:COLLECTION_NAME;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function i3043:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3043:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i3043:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i3043:COLLECTION_NAME, "collection.xconf", $i3043:XCONF),
      xmldb:store($i3043:COLLECTION, "test.xml", $i3043:XML),
      xmldb:reindex($i3043:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function i3043:tearDown() {
    xmldb:remove($i3043:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i3043:COLLECTION_NAME)
};

(:~
 : #3043: entry position via ancestor/preceding-sibling on quote from predicate pattern.
 : quote[ft:query(., "term")] -> result is matched node; ancestor::tei:entry gives parent entry.
 :)
declare %test:assertEquals(1, 2)
function i3043:entrypos-quote-predicate-self() {
    let $doc := doc($i3043:COLLECTION || "/test.xml")
    for $q in $doc//tei:cit/tei:quote[ft:query(., "term")]
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::tei:entry)/preceding-sibling::tei:entry) + 1
};

(:~
 : #3043: entry position on cit from predicate pattern cit[ft:query(quote)]/quote parent.
 : Iterate over quotes; parent cit has ancestor::tei:entry.
 : Workaround when ft:field had docID issues: order by $q/@xml:id instead of ft:field($q, "xmlid").
 :)
declare %test:assertEquals(1, 2)
function i3043:entrypos-cit-predicate-quote-child() {
    let $doc := doc($i3043:COLLECTION || "/test.xml")
    for $q in $doc//tei:cit[ft:query(tei:quote, "term")]/tei:quote
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::tei:entry)/preceding-sibling::tei:entry) + 1
};

(:~
 : Workaround from issue: cit[ft:query(quote)]/quote + order by root($q)/*/@xml:id
 : instead of ft:field. We use $q/@xml:id (avoids ft:field; same effect for sort).
 :)
declare %test:assertEquals(1, 2)
function i3043:entrypos-cit-predicate-workaround-sort() {
    let $doc := doc($i3043:COLLECTION || "/test.xml")
    for $q in $doc//tei:cit[ft:query(tei:quote, "term")]/tei:quote
    order by $q/@xml:id
    return count(head($q/ancestor::tei:entry)/preceding-sibling::tei:entry) + 1
};

(:~
 : Exact workaround: quote[ft:query(.,...)] (buggy pattern) but order by root($q)//tei:quote[@xml:id=string($q/@xml:id)]/@xml:id.
 : Issue used root($o)/*/@xml:id — structure-dependent; we use root+descendant to avoid ft:field.
 :)
declare %test:assertEquals(1, 2)
function i3043:entrypos-quote-predicate-workaround-sort() {
    let $doc := doc($i3043:COLLECTION || "/test.xml")
    for $q in $doc//tei:cit/tei:quote[ft:query(., "term")]
    order by root($q)//tei:quote[@xml:id = $q/@xml:id]/@xml:id
    return count(head($q/ancestor::tei:entry)/preceding-sibling::tei:entry) + 1
};

(:~
 : Baseline: ft:query as direct source — nodes have LuceneMatch; axis navigation should work.
 :)
declare %test:assertEquals(1, 2)
function i3043:entrypos-query-direct-source() {
    let $doc := doc($i3043:COLLECTION || "/test.xml")
    for $q in ft:query($doc//tei:quote, "term")
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::tei:entry)/preceding-sibling::tei:entry) + 1
};
