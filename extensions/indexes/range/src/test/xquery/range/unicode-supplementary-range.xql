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
 : XQsuite tests for Range index handling of Supplementary Multilingual Plane (SMP)
 : and Supplementary Ideographic Plane (SIP) characters.
 :
 : Verifies that the range index finds all 20 SMP/SIP code points from issue #787
 : (regression test; range is reported to work correctly with supplementary characters).
 :
 : @see https://github.com/eXist-db/exist/issues/787
 : @see https://www.unicode.org/roadmaps/smp/
 :)

module namespace unic-smp-r="http://exist-db.org/xquery/range/test/unic-smp-supplementary";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";
import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

(:~
 : All 20 SMP/SIP code points from issue #787, grouped as in the issue report.
 : @return map with keys smp-indexed, smp-dropped, sip-indexed, sip-dropped; values are sequences of xs:integer codepoints
 :)
declare variable $unic-smp-r:CODEPOINTS := map {
    "smp-indexed": (65536, 66321, 66661, 68200, 68608),
    "smp-dropped": (65797, 65930, 128336, 128512, 119558, 128267),
    "sip-indexed": (131072, 131369, 145429, 170811, 178084),
    "sip-dropped": (183618, 178231, 178671, 183785)
};

(:~
 : Flattened sequence of all codepoints in issue order (smp-indexed, smp-dropped, sip-indexed, sip-dropped).
 : @return xs:integer* all 20 codepoints
 :)
declare variable $unic-smp-r:ALL_CODEPOINTS := (
    $unic-smp-r:CODEPOINTS("smp-indexed"),
    $unic-smp-r:CODEPOINTS("smp-dropped"),
    $unic-smp-r:CODEPOINTS("sip-indexed"),
    $unic-smp-r:CODEPOINTS("sip-dropped")
);

(:~
 : Map from codepoint (xs:integer) to group name for informative test output and document attributes.
 : @return map(xs:integer, xs:string) codepoint to "smp-indexed" | "smp-dropped" | "sip-indexed" | "sip-dropped"
 :)
declare variable $unic-smp-r:CP_TO_GROUP := map:merge(
    for $k in map:keys($unic-smp-r:CODEPOINTS)
    return for $cp in $unic-smp-r:CODEPOINTS($k) return map:entry($cp, $k)
);

(:~
 : Test document: one p per supplementary codepoint, with group attribute retained.
 : @return document-node() root with 20 p children, each p has @group and one supplementary character
 :)
declare variable $unic-smp-r:XML as document-node() := document {
    <root>{
        for $cp in $unic-smp-r:ALL_CODEPOINTS
        return <p group="{ $unic-smp-r:CP_TO_GROUP($cp) }">{ codepoints-to-string($cp) }</p>
    }</root>
};

(:~
 : Collection configuration with range index on element p (xs:string).
 : @return element(collection) eXist collection config
 :)
declare variable $unic-smp-r:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="p" type="xs:string"/>
            </range>
        </index>
    </collection>;

(:~ Name of the test collection (no path). :)
declare variable $unic-smp-r:COLLECTION_NAME := "unicode-supplementary-range";

(:~ Full path of the test collection. :)
declare variable $unic-smp-r:COLLECTION := "/db/" || $unic-smp-r:COLLECTION_NAME;

(:~
 : XQsuite setUp: create test and config collections, store document and xconf, reindex.
 :)
declare
    %test:setUp
function unic-smp-r:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $unic-smp-r:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $unic-smp-r:COLLECTION_NAME),
      xmldb:store($unic-smp-r:COLLECTION, "test.xml", $unic-smp-r:XML),
      xmldb:store("/db/system/config/db/" || $unic-smp-r:COLLECTION_NAME, "collection.xconf", $unic-smp-r:xconf),
      xmldb:reindex($unic-smp-r:COLLECTION) )
};

(:~
 : XQsuite tearDown: remove test collection and its config collection.
 :)
declare
    %test:tearDown
function unic-smp-r:tearDown() {
    xmldb:remove($unic-smp-r:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $unic-smp-r:COLLECTION_NAME)
};

(:~
 : Counts how many codepoints in the given group the range index finds (range:eq).
 :
 : @param $group group name: smp-indexed | smp-dropped | sip-indexed | sip-dropped
 : @return xs:string "group: count" e.g. "smp-indexed: 5"
 :)
declare function unic-smp-r:range-finds-count($group as xs:string) as xs:string {
    let $codepoints := $unic-smp-r:CODEPOINTS($group),
        $found := sum(
            for $cp in $codepoints
            return if (count(collection($unic-smp-r:COLLECTION)//p[range:eq(., codepoints-to-string($cp))]) gt 0)
                   then 1 else 0
        )
    return $group || ": " || $found
};

(:~
 : Asserts that the range index finds all supplementary characters in group smp-indexed (5 codepoints).
 : @return xs:string "smp-indexed: 5"
 :)
declare
    %test:assertEquals("smp-indexed: 5")
function unic-smp-r:range-finds-supplementary-smp-indexed() {
    unic-smp-r:range-finds-count("smp-indexed")
};

(:~
 : Asserts that the range index finds all supplementary characters in group smp-dropped (6 codepoints).
 : @return xs:string "smp-dropped: 6"
 :)
declare
    %test:assertEquals("smp-dropped: 6")
function unic-smp-r:range-finds-supplementary-smp-dropped() {
    unic-smp-r:range-finds-count("smp-dropped")
};

(:~
 : Asserts that the range index finds all supplementary characters in group sip-indexed (5 codepoints).
 : @return xs:string "sip-indexed: 5"
 :)
declare
    %test:assertEquals("sip-indexed: 5")
function unic-smp-r:range-finds-supplementary-sip-indexed() {
    unic-smp-r:range-finds-count("sip-indexed")
};

(:~
 : Asserts that the range index finds all supplementary characters in group sip-dropped (4 codepoints).
 : @return xs:string "sip-dropped: 4"
 :)
declare
    %test:assertEquals("sip-dropped: 4")
function unic-smp-r:range-finds-supplementary-sip-dropped() {
    unic-smp-r:range-finds-count("sip-dropped")
};
