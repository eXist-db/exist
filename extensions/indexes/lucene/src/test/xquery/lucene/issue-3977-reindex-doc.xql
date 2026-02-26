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
 : XQSuite regression test for GitHub #3977: xmldb:reindex($collection-uri, $doc-uri)
 : always adds new entries in Lucene index instead of updating existing ones.
 :
 : Bug: Document-level reindex uses addDocument instead of updateDocument (or
 : remove-before-add when reindexing flag is set). Repeating reindex on the same
 : document creates duplicate index entries, inflating facet counts.
 :
 : Reproducer: collection with Lucene facet on root; two docs (test1: empty
 : children, test2: has child); reindex collection, then reindex each doc twice.
 : Expected facet counts 1 each; bug yields 3 each.
 :
 : @see https://github.com/eXist-db/exist/issues/3977
 :)
module namespace i3977 = "http://exist-db.org/xquery/lucene/issue-3977/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(:~ Test documents: test1 has no child elements (empty(./*) = true), test2 has child (empty(./*) = false). :)
declare variable $i3977:TEST1 := document { <root id="1">LuceneTest1</root> };
declare variable $i3977:TEST2 := document { <root id="2"><child/>LuceneTest2</root> };
(:~ joewiz's test: single doc with foo for facet value "bar" (loop reindex pattern). :)
declare variable $i3977:TEST3 := document { <root><foo>bar</foo></root> };

(:~ Collection config: testFacet = empty(./*) for original repro; test-facet = string(./foo) for joewiz loop. :)
declare variable $i3977:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="root">
                    <facet dimension="testFacet" expression="empty(./*)"/>
                    <facet dimension="test-facet" expression="string(./foo)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i3977:COLLECTION_NAME := "lucene-test-issue-3977";
declare variable $i3977:COLLECTION := "/db/" || $i3977:COLLECTION_NAME;

(:~
 : setUp: create collection, config, store test1.xml and test2.xml, reindex collection.
 :)
declare
    %test:setUp
function i3977:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3977:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i3977:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i3977:COLLECTION_NAME, "collection.xconf", $i3977:XCONF),
      xmldb:store($i3977:COLLECTION, "test1.xml", $i3977:TEST1),
      xmldb:store($i3977:COLLECTION, "test2.xml", $i3977:TEST2),
      xmldb:store($i3977:COLLECTION, "test3.xml", $i3977:TEST3),
      xmldb:reindex($i3977:COLLECTION) )
};

(:~
 : tearDown: remove data and config collections.
 :)
declare
    %test:tearDown
function i3977:tearDown() {
    xmldb:remove($i3977:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i3977:COLLECTION_NAME)
};

(:~
 : #3977: After xmldb:reindex(collection) then xmldb:reindex(collection, doc) twice per doc,
 : facet counts must not inflate (update, not add). Bug: duplicate adds yield 3 each for test1/test2.
 : With fix: test1 (true)=1, test2+test3 (false)=2. Returns (facet-true-count, facet-false-count).
 :)
declare
    %test:assertEquals(1, 2)
function i3977:reindex-doc-updates-not-adds() {
    (: Bug-triggering sequence from #3977 (test1, test2; test3 added for joewiz tests) :)
    let $_ := xmldb:reindex($i3977:COLLECTION)
    let $_ := xmldb:reindex($i3977:COLLECTION, "test1.xml")
    let $_ := xmldb:reindex($i3977:COLLECTION, "test1.xml")
    let $_ := xmldb:reindex($i3977:COLLECTION, "test2.xml")
    let $_ := xmldb:reindex($i3977:COLLECTION, "test2.xml")
    let $options := map { "facets": map { "testFacet": () } }
    let $results := collection($i3977:COLLECTION)//root[ft:query(., (), $options)]
    let $testFacet := ft:facets($results, "testFacet", ())
    (: Facet values from empty(./*) are "true" and "false" (see #3977 result). :)
    return (
        $testFacet?true,
        $testFacet?false
    )
};

(:~
 : joewiz's test from #3977 comment: each xmldb:reindex#2 adds duplicate; facet count
 : increments (1,2,3,4,5). With fix, stays (1,1,1,1,1). arity-1 (reindex collection)
 : correctly rebuilds, so facet stays 1. Uses test3.xml with foo=bar.
 : @see https://github.com/eXist-db/exist/issues/3977#issuecomment-886603163
 :)
declare
    %test:assertEquals(1, 1, 1, 1, 1)
function i3977:facets-after-reindex-arity-2-joewiz() {
    let $_ := xmldb:reindex($i3977:COLLECTION)
    for $i in (1 to 5)
    let $hits := collection($i3977:COLLECTION)//root[ft:query(., ())]
    let $facets := ft:facets($hits, "test-facet", ())
    let $_ := xmldb:reindex($i3977:COLLECTION, "test3.xml")
    return $facets?bar
};

(:~ joewiz: reindex#1 (collection) correctly rebuilds; facet count stays 1. :)
declare
    %test:assertEquals(1, 1, 1, 1, 1)
function i3977:facets-after-reindex-arity-1-joewiz() {
    let $_ := xmldb:reindex($i3977:COLLECTION)
    for $i in (1 to 5)
    let $hits := collection($i3977:COLLECTION)//root[ft:query(., ())]
    let $facets := ft:facets($hits, "test-facet", ())
    let $_ := xmldb:reindex($i3977:COLLECTION)
    return $facets?bar
};
