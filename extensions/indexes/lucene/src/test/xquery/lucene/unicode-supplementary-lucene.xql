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
 : XQsuite tests for Lucene index handling of Supplementary Multilingual Plane (SMP)
 : and Supplementary Ideographic Plane (SIP) characters.
 :
 : Verifies that the Lucene full-text index indexes and finds all 20 SMP/SIP
 : code points from issue #787; the issue reports that some are dropped.
 :
 : @see https://github.com/eXist-db/exist/issues/787
 : @see https://www.unicode.org/roadmaps/smp/
 :)

module namespace unic-smp-l="http://exist-db.org/xquery/lucene/test/unic-smp-supplementary";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : All 20 SMP/SIP code points from issue #787, grouped as in the issue report.
 : @return map with keys smp-indexed, smp-dropped, sip-indexed, sip-dropped; values are sequences of xs:integer codepoints
 :)
declare variable $unic-smp-l:CODEPOINTS := map {
    "smp-indexed": (65536, 66321, 66661, 68200, 68608),
    "smp-dropped": (65797, 65930, 128336, 128512, 119558, 128267),
    "sip-indexed": (131072, 131369, 145429, 170811, 178084),
    "sip-dropped": (183618, 178231, 178671, 183785)
};

(:~
 : Flattened sequence of all codepoints in issue order (smp-indexed, smp-dropped, sip-indexed, sip-dropped).
 : @return xs:integer* all 20 codepoints
 :)
declare variable $unic-smp-l:ALL_CODEPOINTS := (
    $unic-smp-l:CODEPOINTS("smp-indexed"),
    $unic-smp-l:CODEPOINTS("smp-dropped"),
    $unic-smp-l:CODEPOINTS("sip-indexed"),
    $unic-smp-l:CODEPOINTS("sip-dropped")
);

(:~
 : Map from codepoint (xs:integer) to group name for informative test output and document attributes.
 : @return map(xs:integer, xs:string) codepoint to "smp-indexed" | "smp-dropped" | "sip-indexed" | "sip-dropped"
 :)
declare variable $unic-smp-l:CP_TO_GROUP := map:merge(
    for $k in map:keys($unic-smp-l:CODEPOINTS)
    return for $cp in $unic-smp-l:CODEPOINTS($k) return map:entry($cp, $k)
);

(:~
 : Test document: one p per supplementary codepoint, with group attribute retained.
 : @return document-node() root with 20 p children, each p has @group and one supplementary character
 :)
declare variable $unic-smp-l:XML as document-node() := document {
    <root>{
        for $cp in $unic-smp-l:ALL_CODEPOINTS
        return <p group="{ $unic-smp-l:CP_TO_GROUP($cp) }">{ codepoints-to-string($cp) }</p>
    }</root>
};

(:~
 : Collection configuration with Lucene full-text index on element p.
 : @return element(collection) eXist collection config
 :)
declare variable $unic-smp-l:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

(:~ Name of the test collection (no path). :)
declare variable $unic-smp-l:COLLECTION_NAME := "lucene-test-unicode-supplementary";

(:~ Full path of the test collection. :)
declare variable $unic-smp-l:COLLECTION := "/db/" || $unic-smp-l:COLLECTION_NAME;

(:~
 : XQsuite setUp: create test and config collections, store document and xconf, reindex.
 :)
declare
    %test:setUp
function unic-smp-l:setup() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $unic-smp-l:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $unic-smp-l:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $unic-smp-l:COLLECTION_NAME, "collection.xconf", $unic-smp-l:xconf),
      xmldb:store($unic-smp-l:COLLECTION, "test.xml", $unic-smp-l:XML),
      xmldb:reindex($unic-smp-l:COLLECTION) )
};

(:~
 : XQsuite tearDown: remove test collection and its config collection.
 :)
declare
    %test:tearDown
function unic-smp-l:tearDown() {
    xmldb:remove($unic-smp-l:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $unic-smp-l:COLLECTION_NAME)
};

(:~
 : Counts how many codepoints in the given group Lucene finds (ft:query).
 :
 : @param $group group name: smp-indexed | smp-dropped | sip-indexed | sip-dropped
 : @return xs:string "group: count" e.g. "smp-indexed: 5"
 :)
declare function unic-smp-l:lucene-finds-count($group as xs:string) as xs:string {
    let $codepoints := $unic-smp-l:CODEPOINTS($group),
        $found := sum(
            for $cp in $codepoints
            return if (count(collection($unic-smp-l:COLLECTION)//p[ft:query(., codepoints-to-string($cp))]) gt 0)
                   then 1 else 0
        )
    return $group || ": " || $found
};

(:~
 : Asserts that Lucene indexes and finds all supplementary characters in group smp-indexed (5 codepoints).
 : @return xs:string "smp-indexed: 5"
 :)
declare
    %test:assertEquals("smp-indexed: 5")
function unic-smp-l:lucene-finds-supplementary-smp-indexed() {
    unic-smp-l:lucene-finds-count("smp-indexed")
};

(:~
 : Asserts that Lucene indexes and finds all supplementary characters in group smp-dropped (6 codepoints).
 : Pending until Lucene fix for issue #787.
 :
 : @return xs:string "smp-dropped: 6"
 :)
declare
    %test:pending("Lucene drops these SMP characters, see #787")
    %test:assertEquals("smp-dropped: 6")
function unic-smp-l:lucene-finds-supplementary-smp-dropped() {
    unic-smp-l:lucene-finds-count("smp-dropped")
};

(:~
 : Asserts that Lucene indexes and finds all supplementary characters in group sip-indexed (5 codepoints).
 : @return xs:string "sip-indexed: 5"
 :)
declare
    %test:assertEquals("sip-indexed: 5")
function unic-smp-l:lucene-finds-supplementary-sip-indexed() {
    unic-smp-l:lucene-finds-count("sip-indexed")
};

(:~
 : Asserts that Lucene indexes and finds all supplementary characters in group sip-dropped (4 codepoints).
 : Pending until Lucene fix for issue #787.
 :
 : @return xs:string "sip-dropped: 4"
 :)
declare
    %test:pending("Lucene drops these SIP characters, see #787")
    %test:assertEquals("sip-dropped: 4")
function unic-smp-l:lucene-finds-supplementary-sip-dropped() {
    unic-smp-l:lucene-finds-count("sip-dropped")
};
