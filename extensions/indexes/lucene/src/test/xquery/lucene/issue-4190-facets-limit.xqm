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
 : XQSuite regression test for GitHub #4190: ft:facets third parameter (limit)
 : returns incorrect results when hits come from a variable. Expected: limit N
 : returns at most N facet entries; without limit returns all.
 :
 : @see https://github.com/eXist-db/exist/issues/4190
 :)
module namespace i4190 = "http://exist-db.org/xquery/lucene/issue-4190/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i4190:COLLECTION := "i4190";

(:~ Items with 20 distinct publishers; Alpha(5), Beta(3), Gamma(2), others(1) each. :)
declare variable $i4190:DATA as document-node() :=
    document {
        <items>{
            ( for $i in 1 to 5 return <item publisher="Alpha">text {$i}</item>,
              for $i in 1 to 3 return <item publisher="Beta">text {$i}</item>,
              for $i in 1 to 2 return <item publisher="Gamma">text {$i}</item>,
              for $p in ("Delta", "Epsilon", "Phi", "Rho", "Sigma", "Tau", "Psi", "Omega", "Xi", "Nu", "Mu", "Lambda", "Kappa", "Iota", "Theta", "Eta", "Zeta")
              return <item publisher="{$p}">text</item> )
        }</items>
    };

declare variable $i4190:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="item">
                    <field name="publisher" expression="string(@publisher)"/>
                    <facet dimension="publisher" expression="string(@publisher)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function i4190:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4190:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4190:COLLECTION),
      xmldb:store("/db/" || $i4190:COLLECTION, "data.xml", $i4190:DATA),
      xmldb:store("/db/system/config/db/" || $i4190:COLLECTION, "collection.xconf", $i4190:XCONF),
      xmldb:reindex("/db/" || $i4190:COLLECTION) )
};

declare
    %test:tearDown
function i4190:tearDown() {
    xmldb:remove("/db/" || $i4190:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4190:COLLECTION)
};

(: No limit: all 20 facets. :)
declare
    %test:assertEquals(20)
function i4190:facets-no-limit() {
    let $hits := collection("/db/" || $i4190:COLLECTION)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher")))
};

(: Limit 10: at most 10 facets (path via variable – bug scenario). :)
declare
    %test:assertEquals(10)
function i4190:facets-limit-10-via-variable() {
    let $articles := collection("/db/" || $i4190:COLLECTION)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    return count(map:keys(ft:facets($hits, "publisher", 10)))
};

(: Limit 1: exactly 1 facet (path via variable – bug scenario). :)
declare
    %test:assertEquals(1)
function i4190:facets-limit-1-via-variable() {
    let $articles := collection("/db/" || $i4190:COLLECTION)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    return count(map:keys(ft:facets($hits, "publisher", 1)))
};

(: Limit 10: direct collection path (workaround – should always work). :)
declare
    %test:assertEquals(10)
function i4190:facets-limit-10-direct-path() {
    let $hits := collection("/db/" || $i4190:COLLECTION)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 10)))
};

(: Limit 5: direct path. :)
declare
    %test:assertEquals(5)
function i4190:facets-limit-5-direct-path() {
    let $hits := collection("/db/" || $i4190:COLLECTION)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 5)))
};

(: Content check: limit 1 via variable returns top facet by count (Alpha with 5). :)
declare
    %test:assertTrue
function i4190:facets-limit-1-content-via-variable() {
    let $articles := collection("/db/" || $i4190:COLLECTION)//item
    let $hits := $articles[ft:query(., (), map { "fields": "publisher" })]
    let $facets := ft:facets($hits, "publisher", 1)
    return count(map:keys($facets)) eq 1 and $facets?Alpha eq 5
};

(: Content check: limit 3 returns top three by count (Alpha, Beta, Gamma) with correct counts. :)
declare
    %test:assertTrue
function i4190:facets-limit-3-content-direct-path() {
    let $hits := collection("/db/" || $i4190:COLLECTION)//item[ft:query(., ())]
    let $facets := ft:facets($hits, "publisher", 3)
    return count(map:keys($facets)) eq 3
        and $facets?Alpha eq 5 and $facets?Beta eq 3 and $facets?Gamma eq 2
};
