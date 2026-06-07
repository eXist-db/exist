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
 : Executable spec for the ES-shaped, map-returning search companion to ft:query-scope.
 :
 : ft:query-scope returns live nodes; this assembles those into an Elasticsearch _search-shaped
 : result map -- the form an API builder (existdb-openapi /api/search, an Oxygen plugin) wants:
 : plain data, no nodes to walk. It composes ft:query-scope + ft:score/ft:field/ft:facets/
 : ft:highlight-field-matches; nothing here reaches into Lucene directly.
 :
 : This is the XQuery reference implementation the design addendum proposes promoting to a native
 : ft:search-scope for performance (a native version builds the map straight off the stored-fields
 : scan, skipping node materialization). The shape and the granularity decision are identical either
 : way -- which is the point of writing it now: to pin the contract and surface the problems.
 :
 : Result envelope (ES field names in parentheses):
 :   map {
 :     "total":     xs:integer,   (: hits.total.value -- see granularity note below :)
 :     "max-score": xs:double,    (: hits.max_score :)
 :     "hits": array {            (: hits.hits[] :)
 :       map {
 :         "uri":       xs:string,   (: the eXist document URI (_index/_id analog) :)
 :         "node-id":   xs:string,   (: the indexed element within that document :)
 :         "score":     xs:double,   (: _score :)
 :         "source":    map(*),      (: _source -- requested stored fields :)
 :         "highlight": map(*)       (: highlight -- requested fields, serialized snippets :)
 :       }* },
 :     "facets": map(*)           (: aggregations -- requested dimensions, value -> count :)
 :   }
 :
 : GRANULARITY (the decision ES never had to make -- see addendum section 4):
 :   eXist indexes per *element occurrence*, so one document yields 1..N Lucene documents. By default
 :   a hit is an indexed *element* (honest to the index, sub-document precision, fast). $spec?collapse
 :   = true() gives the ES-faithful *document* view: group element hits by document URI, keep the
 :   best-scoring element per document, and report total as the distinct-document count. This is
 :   exactly the element-vs-document count discrepancy seen in /api/search (e.g. 454 element hits
 :   collapsing to 224 documents); ES's analog is field-collapse / the top_hits aggregation.
 :)
module namespace m = "http://exist-db.org/xquery/lucene/test/search-scope-map";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $m:COLLECTION := "/db/lucene-test-search-scope-map";
declare variable $m:CONFIG := "/db/system/config/db/lucene-test-search-scope-map";

(: same nested-element fixture as ft-query-scope.xqm: searchable content in para/caption :)
declare variable $m:XCONF :=
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

declare variable $m:DOC1 :=
    <article>
        <title>Working with arrays</title>
        <section><para>The eXist-db array functions let you map and filter array members.</para></section>
        <figure><caption>An array diagram</caption></figure>
    </article>;

declare variable $m:DOC2 :=
    <article>
        <title>Maps in XQuery</title>
        <section><para>map:merge combines maps; array and map are XDM types.</para></section>
    </article>;

declare variable $m:DOC3 :=
    <article>
        <title>Installation</title>
        <section><para>Install eXist-db on your server.</para></section>
    </article>;

(:~
 : The ES-shaped search. $spec controls the result shape:
 :   "fields"    as xs:string*  : stored fields to include in each hit's "source" (default: none)
 :   "highlight" as xs:string*  : fields to highlight (serialized snippets) (default: none)
 :   "facets"    as xs:string*  : facet dimensions to aggregate (default: none)
 :   "limit"     as xs:integer? : cap on returned hits (default: all)
 :   "collapse"  as xs:boolean? : group hits to one-per-document (ES field-collapse) (default: false)
 :)
declare function m:search($scope as xs:string*, $query as item()?, $spec as map(*)?) as map(*) {
    let $spec := ($spec, map {})[1]
    let $element-hits := ft:query-scope($scope, $query)
    let $collapse := ($spec?collapse, false())[1]
    (: ranked hit list -- element granularity, or one representative element per document :)
    let $ranked :=
        if ($collapse) then
            for $group in $element-hits
            group by $uri := document-uri(root($group))
            order by max($group ! ft:score(.)) descending
            return (for $e in $group order by ft:score($e) descending return $e)[1]
        else
            for $h in $element-hits order by ft:score($h) descending return $h
    let $limited :=
        if (exists($spec?limit)) then subsequence($ranked, 1, $spec?limit) else $ranked
    return map {
        "total": if ($collapse)
                 then count(distinct-values($element-hits ! document-uri(root(.))))
                 else count($element-hits),
        "max-score": if (exists($element-hits)) then max($element-hits ! ft:score(.)) else 0,
        "hits": array {
            for $h in $limited
            return map {
                "uri": document-uri(root($h)),
                "node-id": util:node-id($h),
                "score": ft:score($h),
                "source": map:merge(
                    for $f in $spec?fields return map:entry($f, ft:field($h, $f))),
                "highlight": map:merge(
                    for $f in $spec?highlight
                    return map:entry($f, serialize(ft:highlight-field-matches($h, $f))))
            }
        },
        "facets": map:merge(
            for $d in $spec?facets return map:entry($d, ft:facets($element-hits, $d, ())))
    }
};

declare
    %test:setUp
function m:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-search-scope-map")
    let $col := xmldb:create-collection("/db", "lucene-test-search-scope-map")
    return (
        xmldb:store($conf, "collection.xconf", $m:XCONF),
        xmldb:store($col, "doc1.xml", $m:DOC1),
        xmldb:store($col, "doc2.xml", $m:DOC2),
        xmldb:store($col, "doc3.xml", $m:DOC3),
        xmldb:reindex($col)
    )
};

declare
    %test:tearDown
function m:tearDown() {
    if (xmldb:collection-available($m:COLLECTION)) then xmldb:remove($m:COLLECTION) else (),
    if (xmldb:collection-available($m:CONFIG)) then xmldb:remove($m:CONFIG) else ()
};

(: default (element granularity): total counts indexed elements -- 2 paras + 1 caption match "array" :)
declare
    %test:assertEquals(3)
function m:total-is-element-granularity() {
    m:search($m:COLLECTION, "content:(array)", ())?total
};

(: the hits array carries one entry per element hit :)
declare
    %test:assertEquals(3)
function m:hits-count-matches-total() {
    array:size(m:search($m:COLLECTION, "content:(array)", ())?hits)
};

(: every hit has a uri, a node-id, and a positive score :)
declare
    %test:assertTrue
function m:hit-shape-uri-nodeid-score() {
    let $hits := m:search($m:COLLECTION, "content:(array)", ())?hits
    return
        (every $i in (1 to array:size($hits)) satisfies
            exists($hits($i)?uri) and exists($hits($i)?node-id) and $hits($i)?score gt 0)
};

(: max-score is the maximum hit score :)
declare
    %test:assertTrue
function m:max-score-is-max() {
    let $r := m:search($m:COLLECTION, "content:(array)", ())
    let $scores := for $i in (1 to array:size($r?hits)) return $r?hits($i)?score
    return $r?max-score = max($scores)
};

(: requested stored field appears in each hit's "source" (_source analog) :)
declare
    %test:assertTrue
function m:source-includes-requested-field() {
    let $hits := m:search($m:COLLECTION, "content:(filter)", map { "fields": "heading" })?hits
    return $hits(1)?source?heading = "Working with arrays"
};

(: facets aggregation: the kind dimension splits the 3 hits into 2 para + 1 caption :)
declare
    %test:assertEquals(2, 1)
function m:facets-aggregation() {
    let $facets := m:search($m:COLLECTION, "content:(array)", map { "facets": "kind" })?facets
    return ($facets?kind?para, $facets?kind?caption)
};

(: highlight: requested field comes back as a serialized snippet carrying an exist:match :)
declare
    %test:assertTrue
function m:highlight-snippet() {
    let $hits := m:search($m:COLLECTION, "content:(install)", map { "highlight": "content" })?hits
    return contains($hits(1)?highlight?content, "exist:match")
};

(: collapse = true(): ES-faithful document granularity. "content:(array)" hits 3 elements across 2
   documents (doc1 para + caption, doc2 para); collapsed total is the 2 distinct documents. :)
declare
    %test:assertEquals(2)
function m:collapse-total-is-document-granularity() {
    m:search($m:COLLECTION, "content:(array)", map { "collapse": true() })?total
};

(: collapse keeps one representative (best-scoring) element per document :)
declare
    %test:assertEquals(2)
function m:collapse-hits-one-per-document() {
    array:size(m:search($m:COLLECTION, "content:(array)", map { "collapse": true() })?hits)
};

(: limit caps the returned hits without changing total :)
declare
    %test:assertEquals(1)
function m:limit-caps-hits() {
    array:size(m:search($m:COLLECTION, "content:(array)", map { "limit": 1 })?hits)
};
