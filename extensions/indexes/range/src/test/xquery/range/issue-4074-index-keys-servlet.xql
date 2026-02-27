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
 : XQSuite regression test for GitHub #4074: range:index-keys-for-field broken
 : when called from XQueryServlet (or when statically known documents is empty).
 : These tests cover various calling patterns. The XQueryServlet path (empty
 : static docs) is verified by Issue4074IndexKeysServletContextTest.java, which
 : sets setStaticallyKnownDocuments([]) before execution.
 :
 : @see https://github.com/eXist-db/exist/issues/4074
 :)
module namespace i4074 = "http://exist-db.org/xquery/range/issue-4074/test";

import module namespace range = "http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace tei = "http://www.tei-c.org/ns/1.0";

declare variable $i4074:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index xmlns:tei="http://www.tei-c.org/ns/1.0">
            <range>
                <create qname="tei:test">
                    <field name="elem-field" match="tei:elem" type="xs:string" case="no"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $i4074:DATA1 :=
    <test xmlns="http://www.tei-c.org/ns/1.0">
        <elem>a</elem>
        <elem>b</elem>
        <elem>c</elem>
    </test>;

declare variable $i4074:DATA2 :=
    <test xmlns="http://www.tei-c.org/ns/1.0">
        <elem>a</elem>
        <elem>b</elem>
        <elem>c</elem>
        <elem>b</elem>
        <elem>y</elem>
    </test>;

declare variable $i4074:COLLECTION := "i4074";
declare variable $i4074:COLL_PATH := "/db/" || $i4074:COLLECTION;
declare variable $i4074:CONFIG_PATH := "/db/system/config/db/" || $i4074:COLLECTION;
declare variable $i4074:EXPECTED_KEYS := ("a", "b", "c", "y");

declare
    %test:setUp
function i4074:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4074:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4074:COLLECTION),
      xmldb:create-collection($i4074:COLL_PATH, "test2"),
      xmldb:store($i4074:CONFIG_PATH, "collection.xconf", $i4074:XCONF),
      xmldb:store($i4074:COLL_PATH, "test.xml", $i4074:DATA1),
      xmldb:store($i4074:COLL_PATH || "/test2", "test2.xml", $i4074:DATA2),
      xmldb:reindex($i4074:COLL_PATH) )
};

declare
    %test:tearDown
function i4074:tearDown() {
    xmldb:remove($i4074:COLL_PATH),
    xmldb:remove($i4074:CONFIG_PATH)
};

(: #4074: Without context – must return distinct keys (a,b,c,y) when static docs empty or all. :)
declare
    %test:assertTrue
function i4074:no-context-returns-keys() {
    let $result := range:index-keys-for-field("elem-field", function($key, $nums) { $key }, 100)
    return deep-equal(sort($result), sort($i4074:EXPECTED_KEYS))
};

(: #4074: With explicit context – collection/range:index-keys-for-field (path iterates, may have dupes). :)
declare
    %test:assertTrue
function i4074:with-explicit-context() {
    let $result := distinct-values(
        collection($i4074:COLL_PATH)/range:index-keys-for-field("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(sort($result), sort($i4074:EXPECTED_KEYS))
};

declare function i4074:wrap($a as xs:string, $b as function(*), $c as xs:integer) {
    range:index-keys-for-field($a, $b, $c)
};

(: #4074: With named function and context – collection/local:func(...). :)
declare
    %test:assertTrue
function i4074:with-named-function-context() {
    let $result := distinct-values(
        collection($i4074:COLL_PATH)/i4074:wrap("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(sort($result), sort($i4074:EXPECTED_KEYS))
};

(: #4074: With inline function and context – collection/$inlineFunc(...). :)
declare
    %test:assertTrue
function i4074:with-inline-function-context() {
    let $inlineFunc := function($a as xs:string, $b as function(*), $c as xs:integer) {
        range:index-keys-for-field($a, $b, $c)
    }
    let $result := distinct-values(
        collection($i4074:COLL_PATH)/$inlineFunc("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(sort($result), sort($i4074:EXPECTED_KEYS))
};

(: #4074: With dynamic function and context – collection/$func(...). :)
declare
    %test:assertTrue
function i4074:with-dynamic-function-context() {
    let $func := function-lookup(xs:QName("range:index-keys-for-field"), 3)
    let $result := distinct-values(
        collection($i4074:COLL_PATH)/$func("elem-field", function($key, $nums) { $key }, 100)
    )
    return deep-equal(sort($result), sort($i4074:EXPECTED_KEYS))
};
