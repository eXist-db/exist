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
 : Tests for ft:query-scope — index-first Lucene search that returns all matching nodes
 : (any element type) with scores attached, avoiding ft:query's descendant-wildcard score loss.
 :)
module namespace si = "http://exist-db.org/xquery/lucene/test/query-scope";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $si:COLLECTION := "/db/lucene-test-search-index";
declare variable $si:CONFIG := "/db/system/config/db/lucene-test-search-index";

(: searchable content lives in NESTED elements (para, caption) — NOT the document root —
   which is exactly the case where //*[ft:query(., 'field:(...)')] loses ft:score. :)
declare variable $si:XCONF :=
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

declare variable $si:DOC1 :=
    <article>
        <title>Working with arrays</title>
        <section><para>The eXist-db array functions let you map and filter array members.</para></section>
        <figure><caption>An array diagram</caption></figure>
    </article>;

declare variable $si:DOC2 :=
    <article>
        <title>Maps in XQuery</title>
        <section><para>map:merge combines maps; array and map are XDM types.</para></section>
    </article>;

declare variable $si:DOC3 :=
    <article>
        <title>Installation</title>
        <section><para>Install eXist-db on your server.</para></section>
    </article>;

declare
    %test:setUp
function si:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-search-index")
    let $col := xmldb:create-collection("/db", "lucene-test-search-index")
    return (
        xmldb:store($conf, "collection.xconf", $si:XCONF),
        xmldb:store($col, "doc1.xml", $si:DOC1),
        xmldb:store($col, "doc2.xml", $si:DOC2),
        xmldb:store($col, "doc3.xml", $si:DOC3),
        xmldb:reindex($col)
    )
};

declare
    %test:tearDown
function si:tearDown() {
    if (xmldb:collection-available($si:COLLECTION)) then xmldb:remove($si:COLLECTION) else (),
    if (xmldb:collection-available($si:CONFIG)) then xmldb:remove($si:CONFIG) else ()
};

(: finds the nested elements (2 paras + 1 caption) mentioning "array" — name-independent :)
declare
    %test:assertEquals(3)
function si:finds-nested-across-element-types() {
    count(ft:query-scope($si:COLLECTION, "content:(array)"))
};

(: THE KEY PROOF: every hit is scored > 0, even though the matched elements are NESTED
   (para/caption), which is precisely where //*[ft:query(., 'content:(...)')] returns score 0. :)
declare
    %test:assertTrue
function si:scores-nested-elements() {
    let $hits := ft:query-scope($si:COLLECTION, "content:(array)")
    return
        exists($hits) and (every $h in $hits satisfies ft:score($h) gt 0)
};

(: a single query returns more than one DISTINCT element type (para and caption) :)
declare
    %test:assertEquals(2)
function si:name-independent-multiple-element-types() {
    count(distinct-values(
        for $h in ft:query-scope($si:COLLECTION, "content:(array)")
        return local-name($h)
    ))
};

(: hits are ordinary nodes: ft:facets composes on the result :)
declare
    %test:assertEquals(2, 1)
function si:composes-with-facets() {
    let $hits := ft:query-scope($si:COLLECTION, "content:(array)")
    let $kinds := ft:facets($hits, "kind", ())
    return ($kinds?para, $kinds?caption)
};

(: ft:field composes on the result (the stored heading of a matched para) :)
declare
    %test:assertTrue
function si:composes-with-field() {
    let $hit := ft:query-scope($si:COLLECTION, "content:(filter)")[1]
    return ft:field($hit, "heading") = "Working with arrays"
};

(: ft:highlight-field-matches composes on the result :)
declare
    %test:assertTrue
function si:composes-with-highlight() {
    let $hit := ft:query-scope($si:COLLECTION, "content:(install)")[1]
    return exists(ft:highlight-field-matches($hit, "content")//exist:match)
};

(: each hit is the LIVE indexed node, reachable back to its document :)
declare
    %test:assertTrue
function si:returns-live-nodes() {
    let $hit := ft:query-scope($si:COLLECTION, "content:(install)")[1]
    return
        $hit/ancestor::article/title = "Installation"
        and root($hit) instance of document-node()
};

(: empty query matches all indexed nodes in scope :)
declare
    %test:assertTrue
function si:empty-query-matches-all() {
    count(ft:query-scope($si:COLLECTION, ())) ge 4
};

(: ---- 3-arg form: the $options argument (facet drill-down, default-operator) ---- :)

(: facet drill-down via the options map restricts the "content:(array)" hits (2 paras + 1 caption)
   to the "para" facet value -> the 2 paras only. Exercises the 3-arg path and OPTION_FACETS. :)
declare
    %test:assertEquals(2)
function si:options-facet-drilldown-para() {
    count(ft:query-scope($si:COLLECTION, "content:(array)",
        map { "facets": map { "kind": "para" } }))
};

(: same drill-down to the other facet value -> the single caption hit. :)
declare
    %test:assertEquals(1)
function si:options-facet-drilldown-caption() {
    count(ft:query-scope($si:COLLECTION, "content:(array)",
        map { "facets": map { "kind": "caption" } }))
};

(: control (2-arg): eXist's default operator is AND, so "array map" matches only the two
   nodes containing BOTH terms (doc1/para, doc2/para) -- not the array-only caption. :)
declare
    %test:assertEquals(2)
function si:default-operator-is-and-by-default() {
    count(ft:query-scope($si:COLLECTION, "content:(array map)"))
};

(: 3-arg form: the default-operator option is honored. Flipping to OR widens the AND-default
   above (2) to 3 by also matching the array-only caption -- proving the options arg passes through. :)
declare
    %test:assertEquals(3)
function si:options-default-operator-or() {
    count(ft:query-scope($si:COLLECTION, "content:(array map)",
        map { "default-operator": "or" }))
};

(: results are sortable by score (relevance ranking left to the caller) :)
declare
    %test:assertTrue
function si:sortable-by-score() {
    let $ranked :=
        for $h in ft:query-scope($si:COLLECTION, "content:(array)")
        order by ft:score($h) descending
        return ft:score($h)
    return
        every $i in (2 to count($ranked)) satisfies $ranked[$i - 1] ge $ranked[$i]
};
