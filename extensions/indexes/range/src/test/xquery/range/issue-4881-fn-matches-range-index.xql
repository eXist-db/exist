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
 : XQSuite regression test for GitHub #4881: fn:matches with simple range index.
 : //foo[matches(@bar, "^b")] should use new-range index.
 :
 : @see https://github.com/eXist-db/exist/issues/4881
 :)
module namespace i4881 = "http://exist-db.org/xquery/range/issue-4881/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace stats = "http://exist-db.org/xquery/profiling";

declare variable $i4881:DATA := document {
    <root>
        <foo bar="baz"/>
        <foo bar="bat"/>
        <foo bar="Baz"/>
        <foo bar="qux"/>
    </root>
};

declare variable $i4881:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <range>
                <create qname="@bar" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $i4881:COLLECTION := "i4881-fn-matches-range";
declare variable $i4881:COLLECTION_PATH := "/db/" || $i4881:COLLECTION;

declare
    %test:setUp
function i4881:setUp() {
    (xmldb:create-collection("/db/system", "config"),
     xmldb:create-collection("/db/system/config", "db"),
     xmldb:create-collection("/db/system/config/db", $i4881:COLLECTION),
     xmldb:create-collection("/db", $i4881:COLLECTION),
     xmldb:store("/db/system/config/db/" || $i4881:COLLECTION, "collection.xconf", $i4881:XCONF),
     xmldb:store($i4881:COLLECTION_PATH, "test.xml", $i4881:DATA),
     xmldb:reindex($i4881:COLLECTION_PATH))
};

declare
    %test:tearDown
function i4881:tearDown() {
    xmldb:remove($i4881:COLLECTION_PATH),
    xmldb:remove("/db/system/config/db/" || $i4881:COLLECTION)
};

(: Result correctness: fn:matches returns correct nodes. :)
declare
    %test:assertEquals(2)
function i4881:matches-result-correctness() {
    count(collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "^b")])
};

(: Case-insensitive: ^b with "i" matches baz, bat, Baz. :)
declare
    %test:assertEquals(3)
function i4881:matches-result-correctness-case-insensitive() {
    count(collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "^b", "i")])
};

(: Unanchored "b" matches baz, bat (substring), not qux. Fallback to FunMatches when not translatable. :)
declare
    %test:assertEquals(2)
function i4881:matches-result-correctness-unanchored() {
    count(collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "b")])
};

(: Suffix: bar ends with z matches baz, Baz. :)
declare
    %test:assertEquals(2)
function i4881:matches-result-correctness-suffix() {
    count(collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "z$")])
};

(: Exact: bar equals baz. :)
declare
    %test:assertEquals(1)
function i4881:matches-result-correctness-exact() {
    count(collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "^baz$")])
};

(: Baseline: eq uses new-range index. :)
declare
    %test:stats
    %test:args("baz")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i4881:eq-uses-index($term as xs:string) {
    collection($i4881:COLLECTION_PATH)//foo[@bar eq $term]
};

(: #4881: fn:matches with simple qname index should use new-range. :)
declare
    %test:stats
    %test:args("^b")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i4881:matches-uses-index($pattern as xs:string) {
    collection($i4881:COLLECTION_PATH)//foo[matches(@bar, $pattern)]
};

(: Suffix pattern should use index. :)
declare
    %test:stats
    %test:args("z$")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i4881:matches-suffix-uses-index($pattern as xs:string) {
    collection($i4881:COLLECTION_PATH)//foo[matches(@bar, $pattern)]
};

(: Exact pattern should use index. :)
declare
    %test:stats
    %test:args("^baz$")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i4881:matches-exact-uses-index($pattern as xs:string) {
    collection($i4881:COLLECTION_PATH)//foo[matches(@bar, $pattern)]
};

(: Case-insensitive ^b with "i" should use index. :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i4881:matches-case-insensitive-uses-index() {
    collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "^b", "i")]
};

(: Unanchored pattern should NOT use index (fallback to FunMatches). :)
declare
    %test:stats
    %test:assertXPath("not($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function i4881:matches-unanchored-no-index() {
    collection($i4881:COLLECTION_PATH)//foo[matches(@bar, "b")]
};
