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
 : XQSuite tests for util:index-keys on Lucene FT and range indexed nodes (path/qname).
 : Refactored from LuceneFT+range_indexRetrievalTest.xml (TestSet).
 :
 : @author Ron Van den Branden
 :)
module namespace idxk="http://exist-db.org/xquery/lucene/index-keys/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : Collection config: pPath, @typePath, pQname, @typeQname Lucene + range.
 :)
declare variable $idxk:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text match="//pPath"/>
                <text match="//@typePath"/>
                <text qname="pQname"/>
                <text qname="@typeQname"/>
            </lucene>
            <create path="//pPath" type="xs:string"/>
            <create path="//@typePath" type="xs:string"/>
            <create qname="pQname" type="xs:string"/>
            <create qname="@typeQname" type="xs:string"/>
        </index>
    </collection>;

(:~
 : Test document (same for test1 and test2).
 :)
declare variable $idxk:TEST_DOC as element(test) :=
    <test>
        <pPath>
            <el typePath="sometype">some text inside an element</el>
        </pPath>
        <pPath>some text inside a paragraph</pPath>
        <pQname>
            <el typeQname="sometype">some text inside an element</el>
        </pQname>
        <pQname>some text inside a paragraph</pQname>
    </test>;

declare variable $idxk:COLLECTION_NAME := "lucene-test-index-keys";
declare variable $idxk:COLLECTION := "/db/" || $idxk:COLLECTION_NAME;

(:~
 : Callback for util:index-keys.
 :)
declare %private function idxk:term-callback($term as xs:string, $data as xs:int+) as element(entry) {
    <entry>
        <term>{ normalize-space($term) }</term>
        <frequency>{ $data[1] }</frequency>
        <documents>{ $data[2] }</documents>
        <position>{ $data[3] }</position>
    </entry>
};

(:~
 : Expected entries for path-based Lucene index scan.
 :)
declare variable $idxk:EXPECTED_PATH_LUCENE as element(entry)+ := (
    <entry>
        <term>element</term>
        <frequency>2</frequency>
        <documents>2</documents>
        <position>1</position>
    </entry>,
    <entry>
        <term>inside</term>
        <frequency>4</frequency>
        <documents>2</documents>
        <position>2</position>
    </entry>,
    <entry>
        <term>paragraph</term>
        <frequency>2</frequency>
        <documents>2</documents>
        <position>3</position>
    </entry>,
    <entry>
        <term>some</term>
        <frequency>4</frequency>
        <documents>2</documents>
        <position>4</position>
    </entry>,
    <entry>
        <term>text</term>
        <frequency>4</frequency>
        <documents>2</documents>
        <position>5</position>
    </entry>
);

(:~
 : Expected entries for path-based range index scan.
 :)
declare variable $idxk:EXPECTED_PATH_RANGE as element(entry)+ := (
    <entry>
        <term>some text inside an element</term>
        <frequency>2</frequency>
        <documents>2</documents>
        <position>1</position>
    </entry>,
    <entry>
        <term>some text inside a paragraph</term>
        <frequency>2</frequency>
        <documents>2</documents>
        <position>2</position>
    </entry>
);

(:~
 : setUp: create collection, config, store two docs, reindex.
 :)
declare
    %test:setUp
function idxk:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $idxk:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $idxk:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $idxk:COLLECTION_NAME, "collection.xconf", $idxk:XCONF),
      xmldb:store($idxk:COLLECTION, "test1.xml", document { $idxk:TEST_DOC }),
      xmldb:store($idxk:COLLECTION, "test2.xml", document { $idxk:TEST_DOC }),
      xmldb:reindex($idxk:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function idxk:tearDown() {
    xmldb:remove($idxk:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $idxk:COLLECTION_NAME)
};

(:~
 : index scan on path-based Lucene FT indexed nodes
 :)
declare
    %test:assertTrue
function idxk:index-scan-path-lucene() {
    let $callback := util:function(xs:QName("idxk:term-callback"), 2),
        $hits := collection($idxk:COLLECTION)//pPath,
        $result := util:index-keys($hits, '', $callback, 1000, 'lucene-index')
    return deep-equal($result, $idxk:EXPECTED_PATH_LUCENE)
};

(:~
 : index scan on qname-based Lucene FT indexed nodes
 :)
declare
    %test:assertTrue
function idxk:index-scan-qname-lucene() {
    let $callback := util:function(xs:QName("idxk:term-callback"), 2),
        $hits := collection($idxk:COLLECTION)//pQname,
        $result := util:index-keys($hits, '', $callback, 1000, 'lucene-index')
    return deep-equal($result, $idxk:EXPECTED_PATH_LUCENE)
};

(:~
 : index scan on path-based range indexed nodes.
 : Compare term, frequency, documents only (position is enumeration order and may differ).
 :)
declare
    %test:assertTrue
function idxk:index-scan-path-range() {
    let $callback := util:function(xs:QName("idxk:term-callback"), 2),
        $hits := collection($idxk:COLLECTION)//pPath,
        $result := util:index-keys($hits, '', $callback, 1000)
    return deep-equal(
        for $e in $result order by $e/term return <entry><term>{ $e/term/text() }</term><frequency>{ $e/frequency/xs:integer(.) }</frequency><documents>{ $e/documents/xs:integer(.) }</documents></entry>,
        for $e in $idxk:EXPECTED_PATH_RANGE order by $e/term return <entry><term>{ $e/term/text() }</term><frequency>{ $e/frequency/xs:integer(.) }</frequency><documents>{ $e/documents/xs:integer(.) }</documents></entry>
    )
};

(:~
 : index scan on qname-based range indexed nodes.
 : Compare term, frequency, documents only (position is enumeration order and may differ).
 :)
declare
    %test:assertTrue
function idxk:index-scan-qname-range() {
    let $callback := util:function(xs:QName("idxk:term-callback"), 2),
        $hits := collection($idxk:COLLECTION)//pQname,
        $result := util:index-keys($hits, '', $callback, 1000)
    return deep-equal(
        for $e in $result order by $e/term return <entry><term>{ $e/term/text() }</term><frequency>{ $e/frequency/xs:integer(.) }</frequency><documents>{ $e/documents/xs:integer(.) }</documents></entry>,
        for $e in $idxk:EXPECTED_PATH_RANGE order by $e/term return <entry><term>{ $e/term/text() }</term><frequency>{ $e/frequency/xs:integer(.) }</frequency><documents>{ $e/documents/xs:integer(.) }</documents></entry>
    )
};
