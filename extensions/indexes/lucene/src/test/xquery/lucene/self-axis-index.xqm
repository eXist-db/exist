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
 : Lucene field calls `sail:list-length-label(.)` from a DB-stored library (`facet:setup`
 : pattern): one `nodiacritics` analyzer on `text`, relative `at="sai-lib.xql"` on `module`
 : (resolved at parse time from the config collection mirror). Library parameter is `item()`
 : so the Lucene context item (`element()` in the engine) matches.
 :)
module namespace t = "http://exist-db.org/xquery/lucene/self-axis-index";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

import module namespace ft = "http://exist-db.org/xquery/lucene";
import module namespace inspect = "http://exist-db.org/xquery/inspection";

declare variable $t:INDEX_LIB :=
    ``[xquery version "3.1";

module namespace sail="http://exist-db.org/xquery/lucene/self-axis-index-lib";

declare function sail:list-length-label($n as item()) as xs:string {
    if (count($n/item) gt 1) then 'multi' else 'single'
};
]``;

declare variable $t:LIB_NAME := "sai-lib.xql";

declare variable $t:XML := document {
    <root>
        <list id="short">
            <item>1</item>
        </list>
        <list id="long">
            <item>1</item>
            <item>2</item>
        </list>
    </root>
};

declare variable $t:COLL := "/db/test-" || "self-axis-index";
declare variable $t:CONF_COLL := "/db/system/config/db/" || substring-after($t:COLL, "/db/");

declare variable $t:MODULE_AT as xs:anyURI := xs:anyURI("xmldb:exist://" || $t:COLL || "/" || $t:LIB_NAME);

declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer" id="nodiacritics"/>
                <module uri="http://exist-db.org/xquery/lucene/self-axis-index-lib" prefix="sail" at="sai-lib.xql"/>
                <text qname="list" analyzer="nodiacritics">
                    <field name="length" expression="sail:list-length-label(.)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function t:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $_ := xmldb:create-collection("/db", substring-after($t:COLL, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($t:COLL, "/db/"))
    return (
        xmldb:store($t:COLL, $t:LIB_NAME, $t:INDEX_LIB, "application/xquery"),
        xmldb:store($t:CONF_COLL, "collection.xconf", $t:xconf),
        xmldb:store($t:COLL, "test.xml", $t:XML),
        xmldb:reindex($t:COLL)
    )
};

declare
    %test:tearDown
function t:tearDown() {
    if (xmldb:collection-available($t:COLL)) then xmldb:remove($t:COLL) else (),
    if (xmldb:collection-available($t:CONF_COLL)) then xmldb:remove($t:CONF_COLL) else ()
};

(:~ --- Stored library (facet-style artifact) --- :)

declare
    %test:assertExists
function t:stored-index-library-module-exports-a-function() {
    inspect:module-functions($t:MODULE_AT)[1]
};

(:~ --- Collection shape --- :)

declare
    %test:assertEquals(2)
function t:two-list-elements-in-collection() {
    count(collection($t:COLL)//list)
};

declare
    %test:assertEquals(1)
function t:one-list-has-single-item() {
    count(collection($t:COLL)//list[count(item) eq 1])
};

declare
    %test:assertEquals(1)
function t:one-list-has-two-items() {
    count(collection($t:COLL)//list[count(item) eq 2])
};

(:~ --- Lucene field queries: hit counts --- :)

declare
    %test:assertEquals(1)
function t:exactly-one-list-matches-length-multi-query() {
    count(collection($t:COLL)//list[ft:query(., "length:multi")])
};

declare
    %test:assertEquals(1)
function t:exactly-one-list-matches-length-single-query() {
    count(collection($t:COLL)//list[ft:query(., "length:single")])
};

(:~ --- Correct list identity for each query --- :)

declare
    %test:assertEquals("long")
function t:length-multi-query-selects-two-item-list-id() {
    string((collection($t:COLL)//list[ft:query(., "length:multi")])/@id)
};

declare
    %test:assertEquals("short")
function t:length-single-query-selects-one-item-list-id() {
    string((collection($t:COLL)//list[ft:query(., "length:single")])/@id)
};

(:~ --- ft:field matches indexed Lucene field --- :)

declare
    %test:assertEquals("multi")
function t:ft-field-on-multi-hit-eq-multi() {
    let $hit := collection($t:COLL)//list[ft:query(., "length:multi")]
    return
        string(ft:field($hit, "length"))
};

declare
    %test:assertEquals("single")
function t:ft-field-on-single-hit-eq-single() {
    let $hit := collection($t:COLL)//list[ft:query(., "length:single")]
    return
        string(ft:field($hit, "length"))
};

(:~ --- Negative: single-item list must not match multi field query --- :)

declare
    %test:assertEmpty
function t:no-single-item-list-in-length-multi-query() {
    collection($t:COLL)//list[count(item) eq 1][ft:query(., "length:multi")]
};

declare
    %test:assertExists
function t:two-item-list-matches-length-multi-query() {
    collection($t:COLL)//list[count(item) gt 1][ft:query(., "length:multi")]
};

(:~ --- Production-style: main-text hit then order by stored Lucene field (TEI Publisher pattern) --- :)

declare
    %test:assertEquals(2)
function t:text-query-one-matches-both-lists() {
    count(collection($t:COLL)//list[ft:query(., "1")])
};

(:~ Default string order places "multi" before "single", so ids sort long then short. :)
declare
    %test:assertEquals("long short")
function t:order-by-length-field-after-text-query() {
    string-join(
        for $list in collection($t:COLL)//list[ft:query(., "1")]
        order by ft:field($list, "length")
        return string($list/@id),
        " "
    )
};

(:~ --- assertXPath: structured summary (expected vs $result tree) --- :)

declare
    %test:assertXPath("number($result//count[@name eq 'lists']) eq 2")
    %test:assertXPath("number($result//count[@name eq 'multi-hits']) eq 1")
    %test:assertXPath("number($result//count[@name eq 'single-hits']) eq 1")
    %test:assertXPath("string($result//field[@which eq 'multi']) eq 'multi'")
    %test:assertXPath("string($result//field[@which eq 'single']) eq 'single'")
function t:summary-xml-for-field-queries() {
    let $lists := collection($t:COLL)//list
    let $multi := $lists[ft:query(., "length:multi")]
    let $single := $lists[ft:query(., "length:single")]
    return
        <summary>
            <count name="lists">{count($lists)}</count>
            <count name="multi-hits">{count($multi)}</count>
            <count name="single-hits">{count($single)}</count>
            <field which="multi">{string(ft:field($multi, "length"))}</field>
            <field which="single">{string(ft:field($single, "length"))}</field>
        </summary>
};
