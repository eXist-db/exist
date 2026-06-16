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
 : Regression test: facet drill-down must not disable ft:highlight-field-matches.
 :
 : A faceted ft:query wraps the content query in a Lucene DrillDownQuery, which is opaque to the
 : term extraction ft:highlight-field-matches relies on. Storing that wrapped query on the match
 : silently produced empty highlights for every faceted search. The fix keeps the pre-drill-down
 : query for match/highlight extraction while searching with the drill-down query.
 :)
module namespace fdh = "http://exist-db.org/xquery/lucene/test/facet-drilldown-highlight";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $fdh:COLLECTION := "/db/lucene-test-facet-highlight";
declare variable $fdh:CONFIG := "/db/system/config/db/lucene-test-facet-highlight";

declare variable $fdh:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="para">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'para'"/>
                </text>
                <text qname="caption">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'caption'"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $fdh:DOC1 :=
    <article>
        <section><para>The eXist-db array functions let you map and filter array members.</para></section>
        <figure><caption>An array diagram</caption></figure>
    </article>;

declare variable $fdh:DOC2 :=
    <article>
        <section><para>map:merge combines maps; array and map are XDM types.</para></section>
    </article>;

(: A second collection whose index carries a boost (boost="2.0" on the para field). With a boost
 : config present, every query is additionally wrapped in a FunctionScoreQuery, so under facet
 : drill-down the query stored on the match is FunctionScoreQuery(DrillDownQuery(content)). That is
 : the case the FunctionScoreQuery branch in LuceneUtil.extractTerms must unwrap to reach the
 : DrillDownQuery (whose visit() is an opaque leaf); without it, boosted faceted highlights are empty. :)
declare variable $fdh:BOOST-COLLECTION := "/db/lucene-test-facet-highlight-boost";
declare variable $fdh:BOOST-CONFIG := "/db/system/config/db/lucene-test-facet-highlight-boost";

declare variable $fdh:BOOST-XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="para" boost="2.0">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'para'"/>
                </text>
                <text qname="caption">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'caption'"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function fdh:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-facet-highlight")
    let $col := xmldb:create-collection("/db", "lucene-test-facet-highlight")
    let $boostConf := xmldb:create-collection("/db/system/config/db", "lucene-test-facet-highlight-boost")
    let $boostCol := xmldb:create-collection("/db", "lucene-test-facet-highlight-boost")
    return (
        xmldb:store($conf, "collection.xconf", $fdh:XCONF),
        xmldb:store($col, "doc1.xml", $fdh:DOC1),
        xmldb:store($col, "doc2.xml", $fdh:DOC2),
        xmldb:reindex($col),
        xmldb:store($boostConf, "collection.xconf", $fdh:BOOST-XCONF),
        xmldb:store($boostCol, "doc1.xml", $fdh:DOC1),
        xmldb:reindex($boostCol)
    )
};

declare
    %test:tearDown
function fdh:tearDown() {
    if (xmldb:collection-available($fdh:COLLECTION)) then xmldb:remove($fdh:COLLECTION) else (),
    if (xmldb:collection-available($fdh:CONFIG)) then xmldb:remove($fdh:CONFIG) else (),
    if (xmldb:collection-available($fdh:BOOST-COLLECTION)) then xmldb:remove($fdh:BOOST-COLLECTION) else (),
    if (xmldb:collection-available($fdh:BOOST-CONFIG)) then xmldb:remove($fdh:BOOST-CONFIG) else ()
};

(: control: a plain (unfaceted) query highlights fine :)
declare
    %test:assertTrue
function fdh:plain-query-highlights() {
    let $hit := (collection($fdh:COLLECTION)//para[ft:query(., "content:(array)")])[1]
    return exists(ft:highlight-field-matches($hit, "content")//exist:match)
};

(: the regression: a facet drill-down query must STILL highlight (was empty before the fix) :)
declare
    %test:assertTrue
function fdh:faceted-query-highlights() {
    let $opts := map { "facets": map { "kind": "para" } }
    let $hit := (collection($fdh:COLLECTION)//para[ft:query(., "content:(array)", $opts)])[1]
    return exists(ft:highlight-field-matches($hit, "content")//exist:match)
};

(: the highlighted term is the queried term, under drill-down :)
declare
    %test:assertTrue
function fdh:faceted-highlight-marks-the-term() {
    let $opts := map { "facets": map { "kind": "para" } }
    let $hit := (collection($fdh:COLLECTION)//para[ft:query(., "content:(array)", $opts)])[1]
    return lower-case(string(ft:highlight-field-matches($hit, "content")//exist:match[1])) = "array"
};

(: sanity: facet drill-down still SELECTS by facet value (kind=caption keeps the caption hit) :)
declare
    %test:assertEquals(1)
function fdh:drilldown-selects-facet() {
    let $opts := map { "facets": map { "kind": "caption" } }
    return count(collection($fdh:COLLECTION)//caption[ft:query(., "content:(array)", $opts)])
};

(: sanity: facet drill-down still EXCLUDES other facet values (kind=para drops the caption) :)
declare
    %test:assertEquals(0)
function fdh:drilldown-excludes-other-facet() {
    let $opts := map { "facets": map { "kind": "para" } }
    return count(collection($fdh:COLLECTION)//caption[ft:query(., "content:(array)", $opts)])
};

(: regression: boost config + facet drill-down must STILL highlight. With a boost the match query is
   FunctionScoreQuery(DrillDownQuery(content)); the FunctionScoreQuery branch in extractTerms unwraps
   to the DrillDownQuery and then its base query. Empty before the fix (and on develop). :)
declare
    %test:assertTrue
function fdh:faceted-query-highlights-with-boost() {
    let $opts := map { "facets": map { "kind": "para" } }
    let $hit := (collection($fdh:BOOST-COLLECTION)//para[ft:query(., "content:(array)", $opts)])[1]
    return exists(ft:highlight-field-matches($hit, "content")//exist:match)
};

(: the highlighted term is the queried term, under boost + drill-down :)
declare
    %test:assertTrue
function fdh:faceted-highlight-marks-the-term-with-boost() {
    let $opts := map { "facets": map { "kind": "para" } }
    let $hit := (collection($fdh:BOOST-COLLECTION)//para[ft:query(., "content:(array)", $opts)])[1]
    return lower-case(string(ft:highlight-field-matches($hit, "content")//exist:match[1])) = "array"
};
