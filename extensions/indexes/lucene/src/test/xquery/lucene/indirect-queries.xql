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
 : XQSuite tests for direct vs indirect queries on Lucene FT and range indexed element nodes.
 : qname/path; positive match and false match (nested/non-nested attribute).
 : Refactored from indirectQueriesTest.xml (TestSet).
 :
 : @author Ron Van den Branden
 :)
module namespace indq="http://exist-db.org/xquery/lucene/indirect-queries/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: p1, @att1, //p2, //@att2 Lucene + range.
 :)
declare variable $indq:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="p1"/>
                <text qname="@att1"/>
                <text match="//p2"/>
                <text match="//@att2"/>
            </lucene>
            <create qname="p1" type="xs:string"/>
            <create qname="@att1" type="xs:string"/>
            <create path="//p2" type="xs:string"/>
            <create path="//@att2" type="xs:string"/>
        </index>
    </collection>;

(:~
 : Test document.
 :)
declare variable $indq:XML as document-node() :=
    document {
        <test>
            <p1 att1="value1">some text inside a qname-based indexed element</p1>
            <p2 att2="value2">some text inside a path-based indexed element</p2>
        </test>
    };

declare variable $indq:COLLECTION_NAME := "lucene-test-indirect-queries";
declare variable $indq:COLLECTION := "/db/" || $indq:COLLECTION_NAME;

(:~
 : Expected results: direct and indirect both have one p1 (qname match).
 :)
declare variable $indq:EXPECTED_P1_MATCH as element(results) :=
    <results>
        <direct><p1 att1="value1">some text inside a qname-based indexed element</p1></direct>
        <indirect><p1 att1="value1">some text inside a qname-based indexed element</p1></indirect>
    </results>;

(:~
 : Expected results: direct and indirect both empty.
 :)
declare variable $indq:EXPECTED_EMPTY as element(results) :=
    <results><direct/><indirect/></results>;

(:~
 : Expected results: direct and indirect both have one p2 (path match).
 :)
declare variable $indq:EXPECTED_P2_MATCH as element(results) :=
    <results>
        <direct><p2 att2="value2">some text inside a path-based indexed element</p2></direct>
        <indirect><p2 att2="value2">some text inside a path-based indexed element</p2></indirect>
    </results>;

(:~
 : setUp: create collection, config, store doc, reindex.
 :)
declare
    %test:setUp
function indq:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $indq:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $indq:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $indq:COLLECTION_NAME, "collection.xconf", $indq:XCONF),
      xmldb:store($indq:COLLECTION, "test.xml", $indq:XML),
      xmldb:reindex($indq:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function indq:tearDown() {
    xmldb:remove($indq:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $indq:COLLECTION_NAME)
};

(:~
 : [Lucene FT index, qname] in/direct hits on element nodes
 :)
declare
    %test:assertTrue
function indq:lucene-qname-match() {
    let $query := 'qname',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_P1_MATCH)
};

(:~
 : [Lucene FT index, qname] in/direct hits on element nodes, false match condition on value of nested attribute
 :)
declare
    %test:assertTrue
function indq:lucene-qname-false-nested-att() {
    let $query := 'value1',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [Lucene FT index, qname] in/direct hits on element nodes, false match condition on value of non-nested attribute
 :)
declare
    %test:assertTrue
function indq:lucene-qname-false-non-nested-att() {
    let $query := 'value2',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [Lucene FT index, path] in/direct hits on element nodes
 :)
declare
    %test:assertTrue
function indq:lucene-path-match() {
    let $query := 'path',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_P2_MATCH)
};

(:~
 : [Lucene FT index, path] in/direct hits on element nodes, false match condition on value of nested attribute
 :)
declare
    %test:assertTrue
function indq:lucene-path-false-nested-att() {
    let $query := 'value2',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [Lucene FT index, path] in/direct hits on element nodes, false match condition on value of non-nested attribute
 :)
declare
    %test:assertTrue
function indq:lucene-path-false-non-nested-att() {
    let $query := 'value1',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[ft:query(., $query)],
        $hits_indirect := $hits[ft:query(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [range index, qname] in/direct hits on element nodes
 :)
declare
    %test:assertTrue
function indq:range-qname-match() {
    let $query := 'qname',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_P1_MATCH)
};

(:~
 : [range index, qname] in/direct hits on element nodes, false match condition on value of nested attribute
 :)
declare
    %test:assertTrue
function indq:range-qname-false-nested-att() {
    let $query := 'value1',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [range index, qname] in/direct hits on element nodes, false match condition on value of non-nested attribute
 :)
declare
    %test:assertTrue
function indq:range-qname-false-non-nested-att() {
    let $query := 'value2',
        $hits := collection($indq:COLLECTION)//p1,
        $hits_direct := collection($indq:COLLECTION)//p1[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [range index, path] in/direct hits on element nodes
 :)
declare
    %test:assertTrue
function indq:range-path-match() {
    let $query := 'path',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_P2_MATCH)
};

(:~
 : [range index, path] in/direct hits on element nodes, false match condition on value of nested attribute
 :)
declare
    %test:assertTrue
function indq:range-path-false-nested-att() {
    let $query := 'value2',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};

(:~
 : [range index, path] in/direct hits on element nodes, false match condition on value of non-nested attribute
 :)
declare
    %test:assertTrue
function indq:range-path-false-non-nested-att() {
    let $query := 'value1',
        $hits := collection($indq:COLLECTION)//p2,
        $hits_direct := collection($indq:COLLECTION)//p2[matches(., $query)],
        $hits_indirect := $hits[matches(., $query)],
        $result := <results><direct>{ $hits_direct }</direct><indirect>{ $hits_indirect }</indirect></results>
    return deep-equal($result, $indq:EXPECTED_EMPTY)
};
