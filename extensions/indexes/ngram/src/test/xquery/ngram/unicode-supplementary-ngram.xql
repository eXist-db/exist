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
 : XQsuite tests for ngram index handling of Supplementary Multilingual Plane (SMP)
 : and Supplementary Ideographic Plane (SIP) characters.
 :
 : Verifies that ngram:contains finds all 20 SMP/SIP code points from issue #787,
 : and that ngram:wildcard-contains(., '.') matches one supplementary character (no chopping).
 :
 : @see https://github.com/eXist-db/exist/issues/787
 : @see https://www.unicode.org/roadmaps/smp/
 :)

module namespace unic-smp-n="http://exist-db.org/xquery/ngram/test/unic-smp-supplementary";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : All 20 SMP/SIP code points from issue #787, grouped as in the issue report.
 : @return map with keys smp-indexed, smp-dropped, sip-indexed, sip-dropped; values are sequences of xs:integer codepoints
 :)
declare variable $unic-smp-n:CODEPOINTS := map {
    "smp-indexed": (65536, 66321, 66661, 68200, 68608),
    "smp-dropped": (65797, 65930, 128336, 128512, 119558, 128267),
    "sip-indexed": (131072, 131369, 145429, 170811, 178084),
    "sip-dropped": (183618, 178231, 178671, 183785)
};

(:~
 : Flattened sequence of all codepoints in issue order (smp-indexed, smp-dropped, sip-indexed, sip-dropped).
 : @return xs:integer* all 20 codepoints
 :)
declare variable $unic-smp-n:ALL_CODEPOINTS := (
    $unic-smp-n:CODEPOINTS("smp-indexed"),
    $unic-smp-n:CODEPOINTS("smp-dropped"),
    $unic-smp-n:CODEPOINTS("sip-indexed"),
    $unic-smp-n:CODEPOINTS("sip-dropped")
);

(:~
 : Map from codepoint (xs:integer) to group name for informative test output and document attributes.
 : @return map(xs:integer, xs:string) codepoint to "smp-indexed" | "smp-dropped" | "sip-indexed" | "sip-dropped"
 :)
declare variable $unic-smp-n:CP_TO_GROUP := map:merge(
    for $k in map:keys($unic-smp-n:CODEPOINTS)
    return for $cp in $unic-smp-n:CODEPOINTS($k) return map:entry($cp, $k)
);

(:~
 : Test document: one p per supplementary codepoint (with @group), plus two w elements for wildcard tests (one SMP, one SIP).
 : @return document-node() root with 20 p children and 2 w children; each w has @which and one supplementary character
 :)
declare variable $unic-smp-n:XML as document-node() := document {
    <root>{
        for $cp in $unic-smp-n:ALL_CODEPOINTS
        return <p group="{ $unic-smp-n:CP_TO_GROUP($cp) }">{ codepoints-to-string($cp) }</p>,
        <w which="smp">{ codepoints-to-string($unic-smp-n:CODEPOINTS("smp-dropped")[4]) }</w>,
        <w which="sip">{ codepoints-to-string($unic-smp-n:CODEPOINTS("sip-dropped")[1]) }</w>
    }</root>
};

(:~
 : Collection configuration with ngram index on elements p and w.
 : @return element(collection) eXist collection config
 :)
declare variable $unic-smp-n:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <ngram qname="p"/>
            <ngram qname="w"/>
        </index>
    </collection>;

(:~ Name of the test collection (no path). :)
declare variable $unic-smp-n:COLLECTION_NAME := "unicode-supplementary-ngram";

(:~ Full path of the test collection. :)
declare variable $unic-smp-n:COLLECTION := "/db/" || $unic-smp-n:COLLECTION_NAME;

(:~
 : XQsuite setUp: create test and config collections, store document and xconf, reindex.
 :)
declare
    %test:setUp
function unic-smp-n:setup() {
    ( xmldb:create-collection("/db", $unic-smp-n:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $unic-smp-n:COLLECTION_NAME),
      xmldb:store($unic-smp-n:COLLECTION, "test.xml", $unic-smp-n:XML),
      xmldb:store("/db/system/config/db/" || $unic-smp-n:COLLECTION_NAME, "collection.xconf", $unic-smp-n:xconf),
      xmldb:reindex($unic-smp-n:COLLECTION) )
};

(:~
 : XQsuite tearDown: remove test collection and its config collection.
 :)
declare
    %test:tearDown
function unic-smp-n:tearDown() {
    xmldb:remove($unic-smp-n:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $unic-smp-n:COLLECTION_NAME)
};

(:~
 : Counts how many codepoints in the given group ngram:contains finds.
 :
 : @param $group group name: smp-indexed | smp-dropped | sip-indexed | sip-dropped
 : @return xs:string "group: count" e.g. "smp-indexed: 5"
 :)
declare function unic-smp-n:ngram-contains-count($group as xs:string) as xs:string {
    let $codepoints := $unic-smp-n:CODEPOINTS($group),
        $found := sum(
            for $cp in $codepoints
            return if (count(collection($unic-smp-n:COLLECTION)//p[ngram:contains(., codepoints-to-string($cp))]) gt 0)
                   then 1 else 0
        )
    return $group || ": " || $found
};

(:~
 : Asserts that ngram:contains finds all supplementary characters in group smp-indexed (5 codepoints).
 : @return xs:string "smp-indexed: 5"
 :)
declare
    %test:assertEquals("smp-indexed: 5")
function unic-smp-n:ngram-contains-supplementary-smp-indexed() {
    unic-smp-n:ngram-contains-count("smp-indexed")
};

(:~
 : Asserts that ngram:contains finds all supplementary characters in group smp-dropped (6 codepoints).
 : @return xs:string "smp-dropped: 6"
 :)
declare
    %test:assertEquals("smp-dropped: 6")
function unic-smp-n:ngram-contains-supplementary-smp-dropped() {
    unic-smp-n:ngram-contains-count("smp-dropped")
};

(:~
 : Asserts that ngram:contains finds all supplementary characters in group sip-indexed (5 codepoints).
 : @return xs:string "sip-indexed: 5"
 :)
declare
    %test:assertEquals("sip-indexed: 5")
function unic-smp-n:ngram-contains-supplementary-sip-indexed() {
    unic-smp-n:ngram-contains-count("sip-indexed")
};

(:~
 : Asserts that ngram:contains finds all supplementary characters in group sip-dropped (4 codepoints).
 : Pending until ngram fix for sip-dropped in issue #787.
 :
 : @return xs:string "sip-dropped: 4"
 :)
declare
    %test:pending("Ngram fails on sip-dropped characters, see #787")
    %test:assertEquals("sip-dropped: 4")
function unic-smp-n:ngram-contains-supplementary-sip-dropped() {
    unic-smp-n:ngram-contains-count("sip-dropped")
};

(:~
 : Asserts that ngram:wildcard-contains(., '.') matches the single w with SMP character (U+1F600).
 : Uses the main test document (w elements); pending until ngram wildcard fix for #787.
 :
 : @return xs:integer 1 if one match
 :)
declare
    %test:pending("Ngram wildcard chops supplementary characters, see #787")
    %test:assertEquals(1)
function unic-smp-n:ngram-wildcard-one-dot-smp() {
    count(collection($unic-smp-n:COLLECTION)//w[@which eq "smp"][ngram:wildcard-contains(., '.')])
};

(:~
 : Asserts that ngram:wildcard-contains(., '.') matches the single w with SIP character (U+2CD02).
 : Uses the main test document (w elements); pending until ngram wildcard fix for #787.
 :
 : @return xs:integer 1 if one match
 :)
declare
    %test:pending("Ngram wildcard chops supplementary characters, see #787")
    %test:assertEquals(1)
function unic-smp-n:ngram-wildcard-one-dot-sip() {
    count(collection($unic-smp-n:COLLECTION)//w[@which eq "sip"][ngram:wildcard-contains(., '.')])
};
