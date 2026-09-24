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

module namespace ftt="http://exist-db.org/xquery/ft-match/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ftt:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="div">
                    <ignore qname="div"/>
                    <ignore qname="hi"/>
                    <field name="pub-year" expression="date" analyzer="keyword"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $ftt:DATA :=
    <body>
        <div>
            <p>Introduction text</p>
            <div>
                <p>text in nested div and more <hi>text</hi>.</p>
            </div>
        </div>
        <div>
            <!-- foul@0 ... fair@6: reversed relative to the query "fair foul", for #833 -->
            <p>foul deed makes it all seem fair today</p>
        </div>
    </body>;

(:~ Data for util:expand + field-query tests (PR #3467). Dates 1970–1976 in date and p; Nixon in p. :)
declare variable $ftt:FIELD_SAMPLE :=
    <body>
        <div>
            <date>1972</date>
            <p>Study 1</p>
            <p>Forwarded to President Nixon Nov. 9, 1970</p>
        </div>
        <div>
            <date>1976</date>
            <p>Study 2</p>
            <p>Early in January 1971, President Nixon, during a radio-TV address.</p>
        </div>
    </body>;

declare variable $ftt:COLLECTION_NAME := "lucene-test-ft-match";
declare variable $ftt:COLLECTION := "/db/" || $ftt:COLLECTION_NAME;

declare
    %test:setUp
function ftt:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db") ),
    xmldb:create-collection("/db/system/config/db", $ftt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ftt:COLLECTION_NAME, "collection.xconf", $ftt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ftt:COLLECTION_NAME),
    xmldb:store($ftt:COLLECTION, "test.xml", $ftt:DATA),
    xmldb:store($ftt:COLLECTION, "testFields.xml", $ftt:FIELD_SAMPLE),
    xmldb:reindex($ftt:COLLECTION)
};

declare
    %test:tearDown
function ftt:cleanup() {
    xmldb:remove($ftt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftt:COLLECTION_NAME)
};

(:~
 : ft:field on field-query hits. Use ft:query as direct source so LuceneMatch is propagated.
 : Workaround: ft:query with field-only "pub-year:[1970 TO 1980]" returns 0 (filterByIndexType?);
 : ft:query-field returns hits, so use predicate form.
 :)
declare
    %test:assertEquals("1972", "1976")
function ftt:field-retrieve() {
    for $h in collection($ftt:COLLECTION)//div[ft:query-field("pub-year", "[1970 TO 1980]")]
    order by ft:field($h, "pub-year")
    return ft:field($h, "pub-year")
};

(:~
 : Regression: ft:highlight-field-matches with predicate form div[ft:query(., ...)].
 : Passes on develop, fails on upgrade branch (LuceneMatch not propagated).
 : DO NOT change until green.
 :)
declare
    %test:assertEquals(1, 1)
function ftt:field-highlight-field-matches() {
    let $hits := collection($ftt:COLLECTION)//div[ft:query(., "pub-year:1972")],
        $result := ft:highlight-field-matches($hits, "pub-year")
    return (count($hits), count($result//exist:match))
};

(:~
 : Same as field-highlight-field-matches but using ft:query-field as direct source.
 : Workaround: ft:query with field-only returns 0; ft:query-field works.
 :)
declare
    %test:assertEquals(1, 1)
function ftt:field-highlight-field-matches-via-query() {
    let $hits := collection($ftt:COLLECTION)//div[ft:query-field("pub-year", "1972")],
        $result := ft:highlight-field-matches($hits, "pub-year")
    return (count($hits), count($result//exist:match))
};

(:~
 : util:expand on field queries: matches for field criteria should not produce superfluous
 : highlights elsewhere. Query 1–2: Nixon (text) correctly highlights. Query 3–4: pub-year
 : (field-only) should yield 0 exist:match (no text to highlight). Currently: (2, 4, 2, 1).
 :
 : @see https://github.com/eXist-db/exist/pull/3467
 :)
declare
    %test:assertEquals(2, 2, 0, 0)
function ftt:field-highlight() {
    (
        count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., "Nixon")])//exist:match),
        count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., "Nixon AND pub-year:[1970 TO 1980]")])//exist:match),
        count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., "pub-year:[1970 TO 1980]")])//exist:match),
        count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., "pub-year:[1970 TO 1973]")])//exist:match)
    )
};

(:~
 : Check match highlighting: because the inner div is set to "ignore" in the Lucene index,
 : the matching string "text" should not be highlighted.
 :
 : It should be highlighted though if we look at the second result, which is the inner div.
 : The nested <hi> should never be highlighted.
 :
 : Pending: returns (2, 2) instead of (1, 1); ignore logic during util:expand scan differs.
 :)
declare
    %test:args("text")
    %test:pending("Returns (2,2); ignore/expand scan behaviour TBD, see #3467")
    %test:assertEquals(1, 1)
function ftt:highlight($query as xs:string) {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][1])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][2])//exist:match)
};

(:~
 : Asserts that string proximity '"Introduction text"~1' and XML
 : &lt;near slop="1"&gt;&lt;term&gt;Introduction&lt;/term&gt;&lt;term&gt;text&lt;/term&gt;&lt;/near&gt;
 : return identical match counts (from util:expand//exist:match).
 :
 : @see https://github.com/eXist-db/exist/issues/833
 : @return xs:integer+ (match-count for string query, match-count for XML query)
 :)
declare
    %test:assertEquals(1, 1)
function ftt:slop-string-vs-xml-equality() {
    let $queries := (
        '"Introduction text"~1',
        <query><near slop="1"><term>Introduction</term><term>text</term></near></query>
    ),
    $results :=
        for $query in $queries
        let $hits := collection($ftt:COLLECTION)//div[ft:query(., $query)],
            $expanded := util:expand($hits),
            $match-count := count($expanded//exist:match)
        return $match-count
    return ($results[1], $results[2])
};

(:~
 : As slop-string-vs-xml-equality, but with non-adjacent terms ("text" ... "more", 4
 : intervening tokens): exercises the slop tolerance itself, not just merging of an
 : incidentally-adjacent match. Also checks hit-count parity (the underlying query still
 : has to find the node before highlighting can even apply).
 :
 : @see https://github.com/eXist-db/exist/issues/833
 : @return xs:integer+ (hit-count, match-count) for the string query, then the same pair for the XML query
 :)
declare
    %test:assertEquals(1, 1, 1, 1)
function ftt:slop-string-vs-xml-equality-nonadjacent() {
    let $queries := (
        '"text more"~4',
        <query><near slop="4"><term>text</term><term>more</term></near></query>
    ),
    $results :=
        for $query in $queries
        let $hits := collection($ftt:COLLECTION)//div[ft:query(., $query)],
            $hit-count := count($hits),
            $expanded := util:expand($hits),
            $match-count := count($expanded//exist:match)
        return ($hit-count, $match-count)
    return $results
};

(:~
 : Documents that '"a b"~n' and <near slop="n"> (default ordered) genuinely disagree on which
 : documents match when word order varies in the indexed text — not a bug, but two different
 : Lucene slop semantics (PhraseQuery's reordering-tolerant edit distance vs SpanNearQuery's
 : strict positional gap). Text has foul@0 ... fair@6 (reversed vs. the query "fair foul"): the
 : phrase form's edit-distance slop tolerates the reversal at slop=7 (5 base + 2 for the swap);
 : the ordered near form never matches a reversed pair, at any slop.
 :
 : @see https://github.com/eXist-db/exist/issues/833
 : @return xs:integer+ (hit-count for the string query, hit-count for the XML query) at slop=6 (no
 :     match either way) then slop=7 (string matches, XML never does)
 :)
declare
    %test:assertEquals(0, 0, 1, 0)
function ftt:slop-string-vs-xml-reordering-disagreement() {
    (
        count(collection($ftt:COLLECTION)//div[ft:query(., '"fair foul"~6')]),
        count(collection($ftt:COLLECTION)//div[ft:query(., <query><near slop="6"><term>fair</term><term>foul</term></near></query>)]),
        count(collection($ftt:COLLECTION)//div[ft:query(., '"fair foul"~7')]),
        count(collection($ftt:COLLECTION)//div[ft:query(., <query><near slop="7"><term>fair</term><term>foul</term></near></query>)])
    )
};

(:~
 : The phrase-as-near query option makes '"a b"~n' match with <near>'s ordered, non-reordering-
 : tolerant semantics: the reversed-order text from slop-string-vs-xml-reordering-disagreement no
 : longer matches the string-syntax query at slop=7 once the option is set, agreeing with the XML
 : near form (which never matches it, at any slop).
 :
 : @see https://github.com/eXist-db/exist/issues/833
 :)
declare
    %test:assertEquals(0)
function ftt:phrase-as-near-option-fixes-reordering-disagreement() {
    count(collection($ftt:COLLECTION)//div[ft:query(., '"fair foul"~7', map { "phrase-as-near": "yes" })])
};

(:~
 : xmldb:reindex($col, "fulltext") — fulltext-only mode; fulltext search still works.
 : @see plans/lucene10-semantic-vector-search-design.md
 :)
declare
    %test:assertEquals(1)
function ftt:reindex-mode-fulltext-still-searchable() {
    let $_ := xmldb:reindex($ftt:COLLECTION, "fulltext")
    return count(collection($ftt:COLLECTION)//div[ft:query(., "Introduction")])
};