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
 : XQSuite tests for ft:query-field and Lucene field indexing.
 : Merges query-field.xql (ft:query-field context) and lucene-indexing-field.xql (field indexing).
 :)
module namespace qf="http://exist-db.org/xquery/lucene/test/query-field";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

import module namespace ft="http://exist-db.org/xquery/lucene";

(:~ Config for query-field-context: test with testField. :)
declare variable $qf:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene diacritics="no">
                <text field="testField" qname="test"/>
            </lucene>
        </index>
    </collection>;

(:~ Config for field indexing: letter with place, from, to. :)
declare variable $qf:XCONF2 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer" id="nodiacritics"/>
                <text qname="letter" analyzer="nodiacritics">
                    <field name="place" expression="place"/>
                    <field name="from" expression="from" store="no"/>
                    <field name="to" expression="to"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~ Config for #3042/#3043: quote field xmlid from @id. :)
declare variable $qf:XCONF3 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.core.KeywordAnalyzer" id="keyword"/>
                <text qname="quote" analyzer="keyword">
                    <field name="xmlid" expression="@id/string()"/>
                </text>
                <text qname="person" index="no">
                    <field name="de" expression="*[@xml:lang='de']"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $qf:XCONF5 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="test">
                    <field name="sortable" expression="./@sortable/string()" type="xs:string" binary="yes"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $qf:XML1 as document-node() :=
    document {
        <test>
            <p>Rüsselsheim</p>
            <p>Russelsheim</p>
            <p>Māori</p>
            <p>Maori</p>
        </test>
    };

declare variable $qf:XML2 as document-node() := document {
    <letters>
        <letter><from>Hans</from><to>Egon</to><place>Berlin</place></letter>
        <letter><from>Rudi</from><to>Egon</to><place>Berlin</place></letter>
        <letter><from>Susi</from><to>Hans</to><place>Hamburg</place></letter>
    </letters>
};

declare variable $qf:XML3 as document-node() := document {
    <body>
        <cit><quote id="z">term</quote></cit>
        <cit><quote id="a">term</quote></cit>
    </body>
};

declare variable $qf:XML4 as document-node() := document {
    <body>
        <entry n="1"><cit><quote id="a">term</quote></cit></entry>
        <entry n="2"><cit><quote id="z">term</quote></cit></entry>
    </body>
};

declare variable $qf:XML5 as document-node() := document {
    <listPerson>
        <person xml:id="p1">
            <persName xml:lang="de">Anna Berger</persName>
            <persName xml:lang="fra">Anne Berger</persName>
        </person>
        <person xml:id="p2">
            <persName xml:lang="de">Bert Ackermann</persName>
            <persName xml:lang="fra">Bertrand Ackermann</persName>
        </person>
        <person xml:id="p3">
            <persName xml:lang="fra">Claude Dupont</persName>
        </person>
    </listPerson>
};

declare variable $qf:XML6 as document-node() := document {
    <div>
        <test sortable="Adm. 1,10">match</test>
        <test sortable="Bdm. 1,11">match</test>
        <test sortable="Cdm. 1,12">match</test>
        <test sortable="Edm. 1,1">match</test>
        <test sortable="Fdm. 1,2">match</test>
        <test sortable="Gdm. 1,3">match</test>
        <test sortable="Zdm. 1,4">match</test>
        <test sortable="Wdm. 1,5">match</test>
        <test sortable="Odm. 1,6">match</test>
        <test sortable="Ydm. 1,7">match</test>
        <test sortable="Cdm. 1,8">match</test>
        <test sortable="Vdm. 1,9">match</test>
        <test sortable="Pdm. 1,13">match</test>
        <test sortable="Edm. 1,14">match</test>
    </div>
};

declare variable $qf:config-db-path := "/db/system/config/db";
declare variable $qf:test-coll-name := "lucene-test-query-field";
declare variable $qf:letters-coll-name := "lucene-test-query-field-letters";
declare variable $qf:issue-304x-coll-name := "lucene-test-query-field-issue-304x";
declare variable $qf:issue-5431-coll-name := "lucene-test-query-field-issue-5431";
declare variable $qf:conf-coll-path := $qf:config-db-path || "/" || $qf:test-coll-name;
declare variable $qf:letters-conf-path := $qf:config-db-path || "/" || $qf:letters-coll-name;
declare variable $qf:issue-304x-conf-path := $qf:config-db-path || "/" || $qf:issue-304x-coll-name;
declare variable $qf:issue-5431-conf-path := $qf:config-db-path || "/" || $qf:issue-5431-coll-name;

declare
%test:setUp
function qf:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $confCol := xmldb:create-collection($qf:config-db-path, $qf:test-coll-name)
    let $testCol := xmldb:create-collection("/db", $qf:test-coll-name)
    let $lettersConfCol := xmldb:create-collection($qf:config-db-path, $qf:letters-coll-name)
    let $lettersCol := xmldb:create-collection("/db", $qf:letters-coll-name)
    let $issue304xConfCol := xmldb:create-collection($qf:config-db-path, $qf:issue-304x-coll-name)
    let $issue304xCol := xmldb:create-collection("/db", $qf:issue-304x-coll-name)
    let $issue5431ConfCol := xmldb:create-collection($qf:config-db-path, $qf:issue-5431-coll-name)
    let $issue5431Col := xmldb:create-collection("/db", $qf:issue-5431-coll-name)
    return (
        xmldb:store($confCol, "collection.xconf", $qf:XCONF1),
        xmldb:store($testCol, "test1.xml", $qf:XML1),
        xmldb:store($testCol, "test2.xml", $qf:XML1),
        xmldb:store($lettersConfCol, "collection.xconf", $qf:XCONF2),
        xmldb:store($lettersCol, "test.xml", $qf:XML2),
        xmldb:store($issue304xConfCol, "collection.xconf", $qf:XCONF3),
        xmldb:store($issue304xCol, "issue-3042.xml", $qf:XML3),
        xmldb:store($issue304xCol, "issue-3043.xml", $qf:XML4),
        xmldb:store($issue304xCol, "issue-3444.xml", $qf:XML5),
        xmldb:store($issue5431ConfCol, "collection.xconf", $qf:XCONF5),
        xmldb:store($issue5431Col, "test.xml", $qf:XML6),
        xmldb:reindex("/db/" || $qf:test-coll-name),
        xmldb:reindex("/db/" || $qf:letters-coll-name),
        xmldb:reindex("/db/" || $qf:issue-304x-coll-name),
        xmldb:reindex("/db/" || $qf:issue-5431-coll-name)
    )
};

declare
%test:tearDown
function qf:tearDown() {
    ( xmldb:remove("/db/" || $qf:test-coll-name),
      xmldb:remove($qf:conf-coll-path),
      xmldb:remove("/db/" || $qf:letters-coll-name),
      xmldb:remove($qf:letters-conf-path),
      xmldb:remove("/db/" || $qf:issue-304x-coll-name),
      xmldb:remove($qf:issue-304x-conf-path),
      xmldb:remove("/db/" || $qf:issue-5431-coll-name),
      xmldb:remove($qf:issue-5431-conf-path) )
};

(:~ Assert that ft:query-field is only called once for the context sequence, not for each item. :)
declare
%test:stats
%test:assertXPath("$result/stats:index[@type eq 'lucene' and @calls eq '1']")
function qf:query-field-context() {
    count(collection("/db/" || $qf:test-coll-name)/*[ft:query-field("testField", "Rüsselsheim", <options/>)])
};

(:~ place:hamburg should return 1 hit. :)
declare
    %test:assertEquals(1)
function qf:query-field-place-hamburg() {
    count(collection("/db/" || $qf:letters-coll-name)//letter[ft:query(., "place:hamburg")])
};

(:~ place:berlin should return 2 hits. :)
declare
    %test:assertEquals(2)
function qf:query-field-place-berlin() {
    count(collection("/db/" || $qf:letters-coll-name)//letter[ft:query(., "place:berlin")])
};

(:~ from:rudi AND place:berlin should return 1 hit. :)
declare
    %test:assertEquals(1)
function qf:query-field-from-and-place() {
    count(collection("/db/" || $qf:letters-coll-name)//letter[ft:query(., "from:rudi AND place:berlin")])
};

(:~ from:susi AND place:berlin should return 0 (Susi is in Hamburg). :)
declare
    %test:assertEquals(0)
function qf:query-field-mismatch() {
    count(collection("/db/" || $qf:letters-coll-name)//letter[ft:query(., "from:susi AND place:berlin")])
};

(:~ Main content search: "Egon" appears in 2 letters. :)
declare
    %test:assertEquals(2)
function qf:query-main-content() {
    count(collection("/db/" || $qf:letters-coll-name)//letter[ft:query(., "Egon")])
};

(:~ #3042: order by ft:field works for quote hits. :)
declare %test:assertEquals("a", "z")
function qf:issue3042-sort-cit-query-quote-child() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3042.xml")
    for $q in ft:query($doc//cit/quote, "term")
    order by ft:field($q, "xmlid")
    return $q/@id/string()
};

declare %test:assertEquals("a", "z")
function qf:issue3042-sort-quote-query-self() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3042.xml")
    for $q in ft:query($doc//quote, "term")
    order by ft:field($q, "xmlid")
    return $q/@id/string()
};

declare %test:assertEquals("a", "z")
function qf:issue3042-sort-quote-predicate-self() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3042.xml")
    for $q in $doc//quote[ft:query(., "term")]
    order by ft:field($q, "xmlid")
    return $q/@id/string()
};

declare %test:assertEquals("a", "z")
function qf:issue3042-sort-cit-query-quote-predicate() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3042.xml")
    for $q in $doc//cit[ft:query(quote, "term")]/quote
    order by ft:field($q, "xmlid")
    return $q/@id/string()
};

(:~ #3043: ancestor/preceding-sibling position with various query patterns. :)
declare %test:assertEquals(1, 2)
function qf:issue3043-entrypos-quote-predicate-self() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3043.xml")
    for $q in $doc//cit/quote[ft:query(., "term")]
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::entry)/preceding-sibling::entry) + 1
};

declare %test:assertEquals(1, 2)
function qf:issue3043-entrypos-cit-predicate-quote-child() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3043.xml")
    for $q in $doc//cit[ft:query(quote, "term")]/quote
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::entry)/preceding-sibling::entry) + 1
};

declare %test:assertEquals(1, 2)
function qf:issue3043-entrypos-cit-predicate-workaround-sort() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3043.xml")
    for $q in $doc//cit[ft:query(quote, "term")]/quote
    order by $q/@id
    return count(head($q/ancestor::entry)/preceding-sibling::entry) + 1
};

declare %test:assertEquals(1, 2)
function qf:issue3043-entrypos-quote-predicate-workaround-sort() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3043.xml")
    for $q in $doc//cit/quote[ft:query(., "term")]
    order by root($q)//quote[@id = $q/@id]/@id
    return count(head($q/ancestor::entry)/preceding-sibling::entry) + 1
};

declare %test:assertEquals(1, 2)
function qf:issue3043-entrypos-query-direct-source() {
    let $doc := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3043.xml")
    for $q in ft:query($doc//quote, "term")
    order by ft:field($q, "xmlid")
    return count(head($q/ancestor::entry)/preceding-sibling::entry) + 1
};

declare
    %test:assertTrue
function qf:issue3444-query-indirect-no-exception() {
    let $base := doc("/db/" || $qf:issue-304x-coll-name || "/issue-3444.xml")//person
    let $_ := $base[ft:query(., "de:a*", map { "leading-wildcard": "yes" })]
    return true()
};

declare
    %test:assertEquals(2)
function qf:issue3444-query-inline-field-prefix() {
    count(
        doc("/db/" || $qf:issue-304x-coll-name || "/issue-3444.xml")//person[
            ft:query(., "de:a*", map { "leading-wildcard": "yes" })
        ]
    )
};

declare
    %test:assertTrue
function qf:issue5431-binary-field-returns-values() {
    let $hits := collection("/db/" || $qf:issue-5431-coll-name)//test[ft:query(., "match")]
    return $hits[1] ! (count(ft:binary-field(., "sortable", "xs:string")) gt 0)
};

declare
    %test:assertEquals("Adm. 1,10", "Bdm. 1,11", "Cdm. 1,12", "Cdm. 1,8", "Edm. 1,1", "Edm. 1,14", "Fdm. 1,2", "Gdm. 1,3", "Odm. 1,6", "Pdm. 1,13", "Vdm. 1,9", "Wdm. 1,5", "Ydm. 1,7", "Zdm. 1,4")
function qf:issue5431-sorted-by-binary-field() {
    for $hit in collection("/db/" || $qf:issue-5431-coll-name)//test[ft:query(., "match")]
    order by ft:binary-field($hit, "sortable", "xs:string") ascending
    return string(ft:binary-field($hit, "sortable", "xs:string"))
};
