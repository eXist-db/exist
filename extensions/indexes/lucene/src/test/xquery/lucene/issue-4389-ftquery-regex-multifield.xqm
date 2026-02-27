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
 : XQSuite regression test for GitHub #4389: ft:query() returns nothing when
 : using regex in multiple Lucene fields via query string (e.g. lemma:/test/ AND pos:/N/),
 : or regex in any field but the first. XML query form works correctly.
 :
 : Lucene 10 QueryParser syntax: field:/pattern/ for regex; see
 : https://lucene.apache.org/core/10_3_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html
 :
 : @see https://github.com/eXist-db/exist/issues/4389
 :)
module namespace i4389 = "http://exist-db.org/xquery/lucene/issue-4389/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace tei = "http://www.tei-c.org/ns/1.0";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i4389:COLLECTION := "i4389";

(:~ Test data: lemma="test", pos="N" :)
declare variable $i4389:DATA as document-node() :=
    document {
        <tei:fs>
            <tei:f name="lemma">test</tei:f>
            <tei:f name="pos">N</tei:f>
        </tei:fs>
    };

declare variable $i4389:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:tei="http://www.tei-c.org/ns/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="tei:fs" index="no">
                    <field name="lemma" expression="tei:f[@name='lemma']" analyzer="keyword"/>
                    <field name="pos" expression="tei:f[@name='pos']" analyzer="keyword"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function i4389:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4389:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4389:COLLECTION),
      xmldb:store("/db/" || $i4389:COLLECTION, "test.xml", $i4389:DATA),
      xmldb:store("/db/system/config/db/" || $i4389:COLLECTION, "collection.xconf", $i4389:XCONF),
      xmldb:reindex("/db/" || $i4389:COLLECTION) )
};

declare
    %test:tearDown
function i4389:tearDown() {
    xmldb:remove("/db/" || $i4389:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4389:COLLECTION)
};

(:~ No regex: lemma:test AND pos:N - should pass :)
declare
    %test:assertTrue
function i4389:no-regex-lucene-query-string() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., "lemma:test AND pos:N")]
    return count($results) eq 1
};

(:~ No regex: XML query form - should pass :)
declare
    %test:assertTrue
function i4389:no-regex-lucene-query-node() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., <query>
            <bool>
                <term occur="must" field="lemma">test</term>
                <term occur="must" field="pos">N</term>
            </bool>
        </query>)]
    return count($results) eq 1
};

(:~ Regex in first field only: lemma:/test/ AND pos:N - should pass :)
declare
    %test:assertTrue
function i4389:single-regex-lucene-query-string-first-field() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., "lemma:/test/ AND pos:N")]
    return count($results) eq 1
};

(:~ Regex in first field: XML form (lemma regex, pos term) - should pass :)
declare
    %test:assertTrue
function i4389:single-regex-lucene-query-node-first-field() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., <query>
            <bool>
                <regex occur="must" field="lemma">test</regex>
                <term occur="must" field="pos">N</term>
            </bool>
        </query>)]
    return count($results) eq 1
};

(:~ Regex in second field: lemma:test AND pos:/N/ - previously failed with query string; now fixed :)
declare
    %test:assertTrue
function i4389:single-regex-lucene-query-string-second-field() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., "lemma:test AND pos:/N/")]
    return count($results) eq 1
};

(:~ Regex in second field: XML form (lemma term, pos regex) - should pass :)
declare
    %test:assertTrue
function i4389:single-regex-lucene-query-node-second-field() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., <query>
            <bool>
                <term occur="must" field="lemma">test</term>
                <regex occur="must" field="pos">N</regex>
            </bool>
        </query>)]
    return count($results) eq 1
};

(:~ Regex in both fields: lemma:/test/ AND pos:/N/ - previously failed with query string; now fixed :)
declare
    %test:assertTrue
function i4389:multi-regex-lucene-query-string() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., "lemma:/test/ AND pos:/N/")]
    return count($results) eq 1
};

(:~ Regex in both fields: XML form - should pass :)
declare
    %test:assertTrue
function i4389:multi-regex-lucene-query-node() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., <query>
            <bool>
                <regex occur="must" field="lemma">test</regex>
                <regex occur="must" field="pos">N</regex>
            </bool>
        </query>)]
    return count($results) eq 1
};

(:~ Regex with no match returns empty - verifies parser handles non-matching regex :)
declare
    %test:assertTrue
function i4389:regex-no-match-returns-empty() {
    let $results := collection("/db/" || $i4389:COLLECTION)//tei:fs[ft:query(., "lemma:/nomatch/ AND pos:/N/")]
    return count($results) eq 0
};
