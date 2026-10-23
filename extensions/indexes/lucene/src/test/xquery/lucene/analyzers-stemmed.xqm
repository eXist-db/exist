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
 : A query's terms are analyzed once, with their field's analyzer: a stemmed field finds the words whose stem
 : changes if stemmed again, such as "agreed" (stem "agre", stemmed again "agr"), beside fields that aren't stemmed.
 : The fields' names are this test's own, as all collections share one index, whose fields have one schema.
 :)
module namespace stem="http://exist-db.org/xquery/lucene/analyzers-stemmed/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $stem:COLLECTION_NAME := "lucene-test-analyzers-stemmed";
declare variable $stem:COLLECTION := "/db/" || $stem:COLLECTION_NAME;

declare variable $stem:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="stemmed" class="org.apache.lucene.analysis.en.EnglishAnalyzer">
                    <param name="stopwords" type="org.apache.lucene.analysis.CharArraySet"/>
                </analyzer>
                <analyzer id="folded" class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer">
                    <param name="stopwords" type="org.apache.lucene.analysis.CharArraySet"/>
                </analyzer>
                <text qname="div">
                    <field name="stem-exact" expression="."/>
                    <field name="stem-stemmed" expression="." analyzer="stemmed"/>
                    <field name="stem-folded" expression="." analyzer="folded"/>
                    <field name="stem-year" expression="@year"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $stem:XML as document-node() :=
    document {
        <volume>
            <div id="d1" year="1950">The parties agreed to the terms.</div>
            <div id="d2" year="1960">A university delegation visited the capital.</div>
            <div id="d3" year="1970">Negotiations on the treaties continued.</div>
            <div id="d4" year="1980">They met at the Café de Paris.</div>
            <div id="d5" year="1990">Their answer was univocal.</div>
        </volume>
    };

declare
    %test:setUp
function stem:setUp() {
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $stem:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $stem:COLLECTION_NAME, "collection.xconf", $stem:XCONF),
    xmldb:create-collection("/db", $stem:COLLECTION_NAME),
    xmldb:store($stem:COLLECTION, "volume.xml", $stem:XML)
};

declare
    %test:tearDown
function stem:tearDown() {
    xmldb:remove($stem:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $stem:COLLECTION_NAME)
};

declare %private function stem:found($query as xs:string) as xs:string* {
    collection($stem:COLLECTION)//div[ft:query(., $query)]/@id/string()
};

declare
    %test:args("stem-stemmed:agreed")
    %test:assertEquals("d1")
    %test:args("stem-stemmed:agreeing")
    %test:assertEquals("d1")
    %test:args("stem-stemmed:university")
    %test:assertEquals("d2")
    %test:args("stem-stemmed:treaty")
    %test:assertEquals("d3")
    %test:args("stem-stemmed:negotiation")
    %test:assertEquals("d3")
function stem:stemmed-word($query as xs:string) {
    stem:found($query)
};

declare
    %test:args("agreed")
    %test:assertEquals("d1")
    %test:args("university")
    %test:assertEquals("d2")
function stem:stemmed-word-in-query-field($query as xs:string) {
    collection($stem:COLLECTION)//div[ft:query-field("stem-stemmed", $query)]/@id/string()
};

declare
    %test:args('stem-stemmed:"agreed to"')
    %test:assertEquals("d1")
    %test:args('stem-stemmed:"university delegation"')
    %test:assertEquals("d2")
function stem:stemmed-phrase($query as xs:string) {
    stem:found($query)
};

declare
    %test:args("(stem-stemmed:(agreeing) OR stem-exact:(agreeing)) AND stem-year:[1945 TO 1955]")
    %test:assertEquals("d1")
    %test:args("(stem-stemmed:(agreeing) OR stem-exact:(agreeing)) AND stem-year:[1955 TO 1965]")
    %test:assertEmpty
    %test:args("(stem-stemmed:(universities) OR stem-exact:(universities)) AND stem-year:[1955 TO 1965]")
    %test:assertEquals("d2")
function stem:stemmed-in-a-boolean-query($query as xs:string) {
    stem:found($query)
};

declare
    %test:args("stem-exact:agreed")
    %test:assertEquals("d1")
    %test:args("stem-exact:agre")
    %test:assertEmpty
    %test:args("stem-exact:Treaties")
    %test:assertEquals("d3")
    %test:args("stem-exact:treaty")
    %test:assertEmpty
function stem:exact-word($query as xs:string) {
    stem:found($query)
};

declare
    %test:args("stem-folded:café")
    %test:assertEquals("d4")
    %test:args("stem-folded:cafe")
    %test:assertEquals("d4")
    %test:args("stem-exact:café")
    %test:assertEquals("d4")
    %test:args("stem-exact:cafe")
    %test:assertEmpty
function stem:folded-word($query as xs:string) {
    stem:found($query)
};

declare
    %test:args("stem-stemmed:univers*")
    %test:assertEquals("d2")
    %test:args("stem-folded:caf*")
    %test:assertEquals("d4")
function stem:prefix($query as xs:string) {
    stem:found($query)
};

(: a wildcard or prefix term is normalized as its field's analyzer normalizes text, lowercased and, for the folded
 : field, folded, but it is not stemmed: stemming "univers" again would give "univ*", which also matches "univocal"
 : (stem "univoc"), and "universit*" is not a prefix of the stem "univers" whether or not it is stemmed :)
declare
    %test:args("stem-stemmed:Univers*")
    %test:assertEquals("d2")
    %test:args("stem-folded:CAFÉ*")
    %test:assertEquals("d4")
    %test:args("stem-stemmed:universit*")
    %test:assertEmpty
    %test:args("stem-stemmed:univ*")
    %test:assertEquals("d2", "d5")
function stem:prefix-is-normalized-not-stemmed($query as xs:string) {
    stem:found($query)
};
