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
 : XQSuite regression test for GitHub #5043: optimizer should use range index
 : for wildcard paths. $corpus//*/tei:w[@lemma eq $term] should use index
 : (like $corpus//tei:w[@lemma eq $term]); currently wildcard path does not.
 :
 : @see https://github.com/eXist-db/exist/issues/5043
 :)
module namespace i5043 = "http://exist-db.org/xquery/range/issue-5043/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei = "http://www.tei-c.org/ns/1.0";
declare namespace stats = "http://exist-db.org/xquery/profiling";

declare variable $i5043:DATA := document {
    <corpus xmlns:tei="http://www.tei-c.org/ns/1.0">
        <div>
            <tei:w lemma="test">test</tei:w>
            <tei:w lemma="word">word</tei:w>
        </div>
        <section>
            <tei:w lemma="test">test</tei:w>
        </section>
    </corpus>
};

declare variable $i5043:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:tei="http://www.tei-c.org/ns/1.0">
        <index>
            <range>
                <create qname="@lemma" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $i5043:COLLECTION := "i5043-wildcard-path";
declare variable $i5043:COLLECTION_PATH := "/db/" || $i5043:COLLECTION;

declare
    %test:setUp
function i5043:setUp() {
    (xmldb:create-collection("/db/system", "config"),
     xmldb:create-collection("/db/system/config", "db"),
     xmldb:create-collection("/db", $i5043:COLLECTION),
     xmldb:create-collection("/db/system/config/db", $i5043:COLLECTION),
     xmldb:store($i5043:COLLECTION_PATH, "test.xml", $i5043:DATA),
     xmldb:store("/db/system/config/db/" || $i5043:COLLECTION, "collection.xconf", $i5043:XCONF),
     xmldb:reindex($i5043:COLLECTION_PATH))
};

declare
    %test:tearDown
function i5043:tearDown() {
    xmldb:remove($i5043:COLLECTION_PATH),
    xmldb:remove("/db/system/config/db/" || $i5043:COLLECTION)
};

(: Baseline: direct path uses index. :)
declare
    %test:stats
    %test:args("test")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i5043:direct-path-uses-index($term as xs:string) {
    collection($i5043:COLLECTION_PATH)//tei:w[@lemma eq $term]
};

(: #5043: Wildcard path should use index (same as direct path). :)
declare
    %test:stats
    %test:args("test")
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function i5043:wildcard-path-uses-index($term as xs:string) {
    collection($i5043:COLLECTION_PATH)//*/tei:w[@lemma eq $term]
};
