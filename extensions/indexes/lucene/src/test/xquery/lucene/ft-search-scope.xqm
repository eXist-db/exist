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
 : Tests for ft:search-scope — the native, map-returning ES _search-shaped companion to
 : ft:query-scope. Returns map { total, max-score, hits[], facets }; element-granularity by
 : default, document-granularity under collapse.
 :)
module namespace ss = "http://exist-db.org/xquery/lucene/test/search-scope";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $ss:COLLECTION := "/db/lucene-test-search-scope";
declare variable $ss:CONFIG := "/db/system/config/db/lucene-test-search-scope";

(: same nested-element fixture as ft-query-scope.xqm: searchable content in para/caption :)
declare variable $ss:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="para">
                    <field name="content" expression="."/>
                    <field name="heading" expression="ancestor::article/title"/>
                    <facet dimension="kind" expression="'para'"/>
                </text>
                <text qname="caption">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'caption'"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $ss:DOC1 :=
    <article>
        <title>Working with arrays</title>
        <section><para>The eXist-db array functions let you map and filter array members.</para></section>
        <figure><caption>An array diagram</caption></figure>
    </article>;

declare variable $ss:DOC2 :=
    <article>
        <title>Maps in XQuery</title>
        <section><para>map:merge combines maps; array and map are XDM types.</para></section>
    </article>;

declare variable $ss:DOC3 :=
    <article>
        <title>Installation</title>
        <section><para>Install eXist-db on your server.</para></section>
    </article>;

declare
    %test:setUp
function ss:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-search-scope")
    let $col := xmldb:create-collection("/db", "lucene-test-search-scope")
    return (
        xmldb:store($conf, "collection.xconf", $ss:XCONF),
        xmldb:store($col, "doc1.xml", $ss:DOC1),
        xmldb:store($col, "doc2.xml", $ss:DOC2),
        xmldb:store($col, "doc3.xml", $ss:DOC3),
        xmldb:reindex($col)
    )
};

declare
    %test:tearDown
function ss:tearDown() {
    if (xmldb:collection-available($ss:COLLECTION)) then xmldb:remove($ss:COLLECTION) else (),
    if (xmldb:collection-available($ss:CONFIG)) then xmldb:remove($ss:CONFIG) else ()
};

(: result is a single map carrying the ES envelope keys :)
declare
    %test:assertTrue
function ss:result-is-envelope-map() {
    let $r := ft:search-scope($ss:COLLECTION, "content:(array)")
    return $r instance of map(*)
        and (every $k in ("total", "max-score", "hits", "facets") satisfies map:contains($r, $k))
};

(: default (element granularity): total counts indexed elements -- 2 paras + 1 caption match "array" :)
declare
    %test:assertEquals(3)
function ss:total-is-element-granularity() {
    ft:search-scope($ss:COLLECTION, "content:(array)")?total
};

(: the hits array carries one entry per element hit :)
declare
    %test:assertEquals(3)
function ss:hits-count-matches-total() {
    array:size(ft:search-scope($ss:COLLECTION, "content:(array)")?hits)
};

(: every hit has a uri, a node-id, and a positive score :)
declare
    %test:assertTrue
function ss:hit-shape-uri-nodeid-score() {
    let $hits := ft:search-scope($ss:COLLECTION, "content:(array)")?hits
    return
        (every $i in (1 to array:size($hits)) satisfies
            exists($hits($i)?uri) and exists($hits($i)?node-id) and $hits($i)?score gt 0)
};

(: hits are ordered by descending score :)
declare
    %test:assertTrue
function ss:hits-ordered-by-score() {
    let $hits := ft:search-scope($ss:COLLECTION, "content:(array)")?hits
    let $scores := for $i in (1 to array:size($hits)) return $hits($i)?score
    return every $i in (2 to count($scores)) satisfies $scores[$i - 1] ge $scores[$i]
};

(: max-score is the maximum hit score :)
declare
    %test:assertTrue
function ss:max-score-is-max() {
    let $r := ft:search-scope($ss:COLLECTION, "content:(array)")
    let $scores := for $i in (1 to array:size($r?hits)) return $r?hits($i)?score
    return $r?max-score = max($scores)
};

(: requested stored field appears in each hit's "source" (_source analog) :)
declare
    %test:assertTrue
function ss:source-includes-requested-field() {
    let $hits := ft:search-scope($ss:COLLECTION, "content:(filter)", map { "fields": "heading" })?hits
    return $hits(1)?source?heading = "Working with arrays"
};

(: facets aggregation: the kind dimension splits the 3 hits into 2 para + 1 caption :)
declare
    %test:assertEquals(2, 1)
function ss:facets-aggregation() {
    let $facets := ft:search-scope($ss:COLLECTION, "content:(array)", map { "facets": "kind" })?facets
    return ($facets?kind?para, $facets?kind?caption)
};

(: collapse = true(): ES-faithful document granularity. "content:(array)" hits 3 elements across 2
   documents (doc1 para + caption, doc2 para); collapsed total is the 2 distinct documents. :)
declare
    %test:assertEquals(2)
function ss:collapse-total-is-document-granularity() {
    ft:search-scope($ss:COLLECTION, "content:(array)", map { "collapse": true() })?total
};

(: collapse keeps one representative (best-scoring) element per document :)
declare
    %test:assertEquals(2)
function ss:collapse-hits-one-per-document() {
    array:size(ft:search-scope($ss:COLLECTION, "content:(array)", map { "collapse": true() })?hits)
};

(: limit caps the returned hits without changing total :)
declare
    %test:assertEquals(1)
function ss:limit-caps-hits() {
    array:size(ft:search-scope($ss:COLLECTION, "content:(array)", map { "limit": 1 })?hits)
};

declare
    %test:assertEquals(3)
function ss:limit-does-not-change-total() {
    ft:search-scope($ss:COLLECTION, "content:(array)", map { "limit": 1 })?total
};

(: empty query matches all indexed nodes in scope; total is at least the 4 indexed elements :)
declare
    %test:assertTrue
function ss:empty-query-matches-all() {
    ft:search-scope($ss:COLLECTION, ())?total ge 4
};
