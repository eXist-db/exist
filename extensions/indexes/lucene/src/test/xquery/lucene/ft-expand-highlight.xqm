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
 : XQSuite tests for util:expand match-highlighting correctness in combination
 : with Lucene full-text queries. The Java optimisation (LuceneMatchListener
 : term-rewrite cache + empty-termMap short-circuit) must preserve these
 : observable behaviours.
 :
 : Performance assertions intentionally live elsewhere (JMH benchmark, pending
 : an index-benchmark reactor). See #5738 / #6318.
 :
 : @see https://github.com/eXist-db/exist/issues/5738
 : @see https://github.com/eXist-db/exist/pull/6318
 : @see https://github.com/eXist-db/exist/pull/3467
 :)
module namespace feh="http://exist-db.org/xquery/lucene/ft-expand-highlight/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

declare variable $feh:COLL := "lucene-ft-expand-highlight";

(:~
 : Lucene config with both a default text index on <entry> and a named field
 : 'lemma' targeting <orth>. Named-field terms must NOT produce <exist:match>
 : wrappers in util:expand output (per PR #3467) — the optimisation in #6318
 : depends on this so its empty-termMap short-circuit is safe.
 :)
declare variable $feh:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="entry">
                    <field name="text" expression="normalize-space()"/>
                    <field name="lemma" expression=".//form[@type='lemma']/orth"/>
                </text>
            </lucene>
        </index>
    </collection>;

(:~
 : Small entry-shaped corpus. Two entries match 'aword42' on the lemma form,
 : and the body text repeats the headword so util:expand has multiple
 : positions to wrap. Kept small because correctness — not throughput — is
 : what this xqsuite verifies.
 :)
declare variable $feh:DATA as document-node() := document {
    <dict>
        <entry xml:id="e42">
            <form type="lemma"><orth>aword42</orth></form>
            <sense><def>Definition for aword42. The headword aword42 appears here and aword42 again.</def></sense>
        </entry>
        <entry xml:id="e7">
            <form type="lemma"><orth>aword7</orth></form>
            <sense><def>Definition for aword7. Unrelated body text without the target headword.</def></sense>
        </entry>
        <entry xml:id="e99">
            <form type="lemma"><orth>bword99</orth></form>
            <sense><def>Definition for bword99. Mentions aword42 inside an unrelated entry.</def></sense>
        </entry>
    </dict>
};

declare
    %test:setUp
function feh:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $feh:COLL),
      xmldb:create-collection("/db/system/config/db", $feh:COLL),
      xmldb:store("/db/system/config/db/" || $feh:COLL, "collection.xconf", $feh:XCONF),
      xmldb:store("/db/" || $feh:COLL, "dict.xml", $feh:DATA) )
};

declare
    %test:tearDown
function feh:tearDown() {
    ( xmldb:remove("/db/" || $feh:COLL),
      xmldb:remove("/db/system/config/db/" || $feh:COLL) )
};

(:~
 : #3467: A named-field query (lemma:aword42) targets configured-field
 : metadata, so util:expand must NOT wrap any token in <exist:match>. The
 : #6318 optimisation's empty-termMap short-circuit relies on this property.
 :)
declare %test:assertEquals(0) function feh:named-field-query-produces-no-highlights() {
    let $hit := subsequence(collection("/db/" || $feh:COLL)//entry[ft:query(., "lemma:aword42")], 1, 1)
    return count(util:expand($hit)//exist:match)
};

(:~
 : Sanity check: an implicit-field query against the same hit DOES produce
 : exist:match wrappers. Verifies the short-circuit doesn't over-suppress
 : highlighting for the common case.
 :)
declare %test:assertTrue function feh:implicit-field-query-still-highlights() {
    let $hit := subsequence(collection("/db/" || $feh:COLL)//entry[ft:query(., "aword42")], 1, 1)
    return count(util:expand($hit)//exist:match) ge 1
};

(:~
 : The path the term-rewrite cache exercises most: util:expand applied to a
 : sequence of hits must produce the same total number of exist:match
 : wrappers as the per-hit for-loop equivalent.
 :)
declare %test:assertTrue function feh:batch-expand-matches-per-hit-for-loop() {
    let $hits := collection("/db/" || $feh:COLL)//entry[ft:query(., "aword42")]
    let $for-loop-count := sum(for $h in $hits return count(util:expand($h)//exist:match))
    let $batch-count := count(util:expand($hits)//exist:match)
    return $batch-count eq $for-loop-count and $batch-count ge 1
};
