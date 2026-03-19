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
 : XQSuite tests for Lucene analyzers (German, Whitespace, Keyword, query-analyzer-id, fields).
 : Refactored from analyzers.xml (TestSet).
 : Includes #2781 diacritics+truncation with German/Whitespace/Standard.
 :
 : @author Wolfgang Meier
 : @see https://github.com/eXist-db/exist/issues/2781
 :)
module namespace anix="http://exist-db.org/xquery/lucene/analyzers-index/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ Collection config: l (line, de with l_ws, l_nostop), @n (lineno keyword). :)
declare variable $anix:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <analyzer id="de" class="org.apache.lucene.analysis.de.GermanAnalyzer"/>
                <analyzer id="de-nostop" class="org.apache.lucene.analysis.de.GermanAnalyzer">
                    <param name="stopwords" type="org.apache.lucene.analysis.CharArraySet"/>
                </analyzer>
                <text qname="l" field="line"/>
                <text qname="l" analyzer="de">
                    <field name="l_ws" analyzer="ws"/>
                    <field name="l_nostop" analyzer="de-nostop"/>
                </text>
                <text field="lineno" qname="@n" analyzer="keyword"/>
                <text qname="p" analyzer="de-nostop"/>
            </lucene>
        </index>
    </collection>;

declare variable $anix:XCONF_PL as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="pl-nostop" class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer">
                    <param name="stopwords" type="org.apache.lucene.analysis.CharArraySet"/>
                </analyzer>
                <text qname="l" analyzer="pl-nostop"/>
            </lucene>
        </index>
    </collection>;

(:~ German poetry test document. :)
declare variable $anix:XML as document-node() :=
    document {
        <text>
            <body>
                <lg>
                    <l n="l1.1">Habe nun, ach! Philosophie,</l>
                    <l n="l1.2">Juristerei und Medizin,</l>
                    <l n="l1.3">Und leider auch Theologie</l>
                    <l n="l1.4">Durchaus studiert, mit heißem Bemühn.</l>
                    <l n="l1.5">Da steh ich nun, ich armer Tor!</l>
                    <l n="l1.6">Und bin so klug als wie zuvor;</l>
                    <l n="l1.7">Heiße Magister, heiße Doktor gar</l>
                    <l n="l1.8">Und ziehe schon an die zehen Jahr</l>
                    <l n="l1.9">Herauf, herab und quer und krumm</l>
                    <l n="l1.10">Meine Schüler an der Nase herum –</l>
                    <l n="l1.11">Und sehe, daß wir nichts wissen können!</l>
                    <l n="l1.12">Das will mir schier das Herz verbrennen.</l>
                </lg>
                <p>Zwar bin ich gescheiter als all die Laffen,</p>
            </body>
        </text>
    };

declare variable $anix:XML_PL :=
    document {
        <text>
            <body>
                <lg>
                    <l>Stoi na stacji lokomotywa,</l>
                    <l>Ciężka, ogromna i pot z niej spływa:</l>
                    <l>Tłusta oliwa.</l>
                    <l>Stoi i sapie, dyszy i dmucha,</l>
                    <l>Żar z rozgrzanego jej brzucha bucha:</l>
                    <l>Buch - jak gorąco!</l>
                    <l>Uch - jak gorąco!</l>
                    <l>Puff - jak gorąco!</l>
                    <l>Uff - jak gorąco!</l>
                    <l>Już ledwo sapie, już ledwo zipie,</l>
                    <l>A jeszcze palacz węgiel w nią sypie.</l>
                </lg>
            </body>
        </text>
    };

declare variable $anix:COLLECTION_NAME := "lucene-test-analyzers-index";
declare variable $anix:COLLECTION_NAME_PL := "lucene-test-analyzers-index-pl";
declare variable $anix:COLLECTION_NAME_2781 := "lucene-test-analyzers-2781";
declare variable $anix:COLLECTION := "/db/" || $anix:COLLECTION_NAME;
declare variable $anix:COLLECTION_PL := "/db/" || $anix:COLLECTION_NAME_PL;
declare variable $anix:COLLECTION_2781 := "/db/" || $anix:COLLECTION_NAME_2781;

(: #2781: default, german, ws fields on p. :)
declare variable $anix:XCONF_2781 as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <lucene>
                <analyzer id="german" class="org.apache.lucene.analysis.de.GermanAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text field="default" qname="p"/>
                <text field="german" qname="p" analyzer="german"/>
                <text field="ws" qname="p" analyzer="ws"/>
            </lucene>
        </index>
    </collection>;

declare variable $anix:XML_2781 as document-node() := document {
    <doc><p>Röntgenbilder der Handschriften angefertigt.</p></doc>
};

(:~ setUp: create collection, config, store doc, reindex.
 : @return empty sequence
 :)
declare
    %test:setUp
function anix:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $anix:COLLECTION_NAME),
      xmldb:create-collection("/db", $anix:COLLECTION_NAME_PL),
      xmldb:create-collection("/db", $anix:COLLECTION_NAME_2781),
      xmldb:create-collection("/db/system/config/db", $anix:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $anix:COLLECTION_NAME_PL),
      xmldb:create-collection("/db/system/config/db", $anix:COLLECTION_NAME_2781),
      xmldb:store("/db/system/config/db/" || $anix:COLLECTION_NAME, "collection.xconf", $anix:XCONF),
      xmldb:store("/db/system/config/db/" || $anix:COLLECTION_NAME_PL, "collection.xconf", $anix:XCONF_PL),
      xmldb:store("/db/system/config/db/" || $anix:COLLECTION_NAME_2781, "collection.xconf", $anix:XCONF_2781),
      xmldb:store($anix:COLLECTION, "text.xml", $anix:XML),
      xmldb:store($anix:COLLECTION_PL, "text_pl.xml", $anix:XML_PL),
      xmldb:store($anix:COLLECTION_2781, "test.xml", $anix:XML_2781),
      xmldb:reindex($anix:COLLECTION_PL),
      xmldb:reindex($anix:COLLECTION),
      xmldb:reindex($anix:COLLECTION_2781) )
};

(:~ tearDown: remove data and config collections.
 : @return empty sequence
 :)
declare
    %test:tearDown
function anix:tearDown() {
    ( xmldb:remove($anix:COLLECTION),
      xmldb:remove("/db/system/config/db/" || $anix:COLLECTION_NAME),
      xmldb:remove($anix:COLLECTION_PL),
      xmldb:remove("/db/system/config/db/" || $anix:COLLECTION_NAME_PL),
      xmldb:remove($anix:COLLECTION_2781),
      xmldb:remove("/db/system/config/db/" || $anix:COLLECTION_NAME_2781) )
};

(:~ German Analyzer: standard search. :)
declare %test:assertTrue function anix:german-standard-search() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'philosophie')]
    return deep-equal($result, <l n="l1.1">Habe nun, ach! Philosophie,</l>)
};

(:~ German Analyzer: search on qname index, no field. :)
declare %test:assertTrue function anix:german-standard-search-qname() {
    let $result := doc($anix:COLLECTION || "/text.xml")//p[ft:query(., 'gescheit')]
    return deep-equal($result, <p>Zwar bin ich gescheiter als all die Laffen,</p>)
};

(:~ German Analyzer: search on qname index, no field, no stopwords. :)
declare %test:assertTrue function anix:german-standard-search-qname-nostop() {
    let $result := doc($anix:COLLECTION || "/text.xml")//p[ft:query(., 'die')]
    return deep-equal($result, <p>Zwar bin ich gescheiter als all die Laffen,</p>)
};

(:~ No Diacritics Standard Analyzer: search on qname index, no field. :)
declare %test:assertTrue function anix:no-diacritics-standard-search-qname() {
    let $result := doc($anix:COLLECTION_PL || "/text_pl.xml")//l[ft:query(., 'Ciezka spływa')]
    return deep-equal($result, <l>Ciężka, ogromna i pot z niej spływa:</l>)
};

(:~ No Diacritics Standard Analyzer: search on qname index, no field, phrase search. :)
declare %test:assertTrue function anix:no-diacritics-standard-search-qname-phrase() {
    let $result := doc($anix:COLLECTION_PL || "/text_pl.xml")//l[ft:query(., '"wegiel w nia sypie"')]
    return deep-equal($result, <l>A jeszcze palacz węgiel w nią sypie.</l>)
};

(:~ No Diacritics Standard Analyzer: search on qname index, no field, no stopwords. :)
declare %test:assertTrue function anix:no-diacritics-standard-search-qname-nostop() {
    let $result := doc($anix:COLLECTION_PL || "/text_pl.xml")//l[ft:query(., 'w')]
    return deep-equal($result, <l>A jeszcze palacz węgiel w nią sypie.</l>)
};

(:~ German Analyzer: stemmed verb. :)
declare %test:assertTrue function anix:german-stemmed-verb() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'jahre')]
    return deep-equal($result, <l n="l1.8">Und ziehe schon an die zehen Jahr</l>)
};

(:~ German Analyzer: plural finds singular. :)
declare %test:assertTrue function anix:german-plural-finds-singular() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'herzen')]
    return deep-equal($result, <l n="l1.12">Das will mir schier das Herz verbrennen.</l>)
};

(:~ query-analyzer-id de (options as XML). :)
declare %test:assertTrue function anix:query-analyzer-id-de-xml() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'herzen', <options><query-analyzer-id>de</query-analyzer-id></options>)]
    return deep-equal($result, <l n="l1.12">Das will mir schier das Herz verbrennen.</l>)
};

(:~ query-analyzer-id:de :)
declare %test:assertTrue function anix:query-analyzer-id-de-map() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'herzen', map { "query-analyzer-id": "de" })]
    return deep-equal($result, <l n="l1.12">Das will mir schier das Herz verbrennen.</l>)
};

(:~ query-analyzer-id keyword (options as XML). :)
declare %test:assertEmpty function anix:query-analyzer-id-keyword-xml() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'herzen', <options><query-analyzer-id>keyword</query-analyzer-id></options>)]
};

(:~ query-analyzer-id:keyword :)
declare %test:assertEmpty function anix:query-analyzer-id-keyword-map() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'herzen', map { "query-analyzer-id": "keyword" })]
};

(:~ query new field "l_no-stop" with the GermanAnalyzer without stopwords :)
declare %test:assertTrue function anix:query-field-l-nostop() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'l_nostop:(ich OR bin)')]
    return deep-equal($result, (<l n="l1.5">Da steh ich nun, ich armer Tor!</l>, <l n="l1.6">Und bin so klug als wie zuvor;</l>))
};

(:~ query new field "l_no-stop" with the default GermanAnalyzer :)
declare %test:assertEmpty function anix:query-field-l-nostop-de() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., 'l_nostop:(ich OR bin)', map { "query-analyzer-id": "de" })]
};

(:~ query new field "l_ws" with the WhitespaceAnalyzer - no result :)
declare %test:assertEmpty function anix:query-field-l-ws-no-result() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., "l_ws:(nun\!)")]
};

(:~ query new field "l_ws" with GermanAnalyzer :)
declare %test:assertEmpty function anix:query-field-l-ws-de-no-result() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., "l_ws:(nun\!)", map { "query-analyzer-id": "de" })]
};

(:~ query new field "l_ws" with the WhitespaceAnalyzer - 2 results :)
declare %test:assertTrue function anix:query-field-l-ws-two-results() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., "l_ws:(nun,)")]
    return deep-equal($result, (<l n="l1.1">Habe nun, ach! Philosophie,</l>, <l n="l1.5">Da steh ich nun, ich armer Tor!</l>))
};

(:~ query new field "l_ws" with the GermanAnalyzer - still no results :)
declare %test:assertEmpty function anix:query-field-l-ws-de-still-no-results() {
    doc($anix:COLLECTION || "/text.xml")//l[ft:query(., "l_ws:(nun,)", map { "query-analyzer-id": "de" })]
};

(:~ query new field "l_ws" with the GermanAnalyzer and wildcard - 2 results :)
declare %test:assertTrue function anix:query-field-l-ws-de-wildcard() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query(., "l_ws:(nun*)", map { "query-analyzer-id": "de" })]
    return deep-equal($result, (<l n="l1.1">Habe nun, ach! Philosophie,</l>, <l n="l1.5">Da steh ich nun, ich armer Tor!</l>))
};

(:~ Query field with standard analyzer, no match :)
declare %test:assertEmpty function anix:query-field-line-no-match() {
    ft:query-field("line", "herzen")
};

(:~ Query field with query analyzer overridden (options as map) :)
declare %test:assertTrue function anix:query-field-line-map-de() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query-field("line", "herzen", map { "query-analyzer-id": "de" })],
        $expected := doc($anix:COLLECTION || "/text.xml")//l[@n = "l1.12"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(:~ Query field with query analyzer overridden (options as xml) :)
declare %test:assertTrue function anix:query-field-line-xml-de() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query-field("line", "herzen", <options><query-analyzer-id>de</query-analyzer-id></options>)],
        $expected := doc($anix:COLLECTION || "/text.xml")//l[@n = "l1.12"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(:~ Query field with standard analyzer and without context :)
declare %test:assertTrue function anix:query-field-line-klug() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query-field("line", 'klug')],
        $expected := doc($anix:COLLECTION || "/text.xml")//l[@n = "l1.6"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(:~ Query field with keyword analyzer, no match :)
declare %test:assertEmpty function anix:query-field-lineno-no-match() {
    ft:query-field("lineno", "10")
};

(:~ Query field with keyword analyzer and without context :)
declare %test:assertTrue function anix:query-field-lineno-without-context() {
    let $result := ft:query-field("lineno", "l1.10")/..,
        $expected := doc($anix:COLLECTION || "/text.xml")//l[@n = "l1.10"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(:~ Query field with keyword analyzer and context :)
declare %test:assertTrue function anix:query-field-lineno-with-context() {
    let $result := doc($anix:COLLECTION || "/text.xml")//l[ft:query-field("lineno", "l1.10")],
        $expected := doc($anix:COLLECTION || "/text.xml")//l[@n = "l1.10"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(: --- #2781: Diacritics and truncation with German/Whitespace/Standard --- :)
declare %test:assertExists function anix:diacritics-trunc-default() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("default", "Röntgen*")]
};

declare %test:assertExists function anix:diacritics-trunc-german() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("german", "Röntgen*")]
};

declare %test:assertExists function anix:diacritics-trunc-ws() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("ws", "Röntgen*")]
};

declare %test:assertExists function anix:diacritics-exact-default() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("default", "Röntgenbilder")]
};

declare %test:assertExists function anix:diacritics-exact-german() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("german", "Röntgenbilder")]
};

declare %test:assertExists function anix:diacritics-exact-ws() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("ws", "Röntgenbilder")]
};

declare %test:assertExists function anix:ascii-trunc-default() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("default", "Hand*")]
};

declare %test:assertExists function anix:ascii-trunc-german() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("german", "Hand*")]
};

declare %test:assertExists function anix:ascii-trunc-ws() {
    collection($anix:COLLECTION_2781)//p[ft:query-field("ws", "Hand*")]
};
