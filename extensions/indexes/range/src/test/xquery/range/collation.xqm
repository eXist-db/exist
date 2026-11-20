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
 : A collation argument the range index cannot honour: the index answers only when it compares in that collation, and
 : otherwise the comparison is evaluated as written. Without a collation argument the index keeps its own comparison
 : (case="no", its collation), by design. Expected counts follow the spec: with a primary-strength collation, 'Apple'
 : and 'apple' both match.
 :)
module namespace rcol="http://exist-db.org/xquery/range/collation/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $rcol:PRIMARY := "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary";
declare variable $rcol:CODEPOINT := "http://www.w3.org/2005/xpath-functions/collation/codepoint";

declare variable $rcol:DATA := <doc><w k="Apple">Apple</w><w k="apple">apple</w><w k="Banana">Banana</w></doc>;

(: collection name -> index configuration; "none" has no configuration :)
declare variable $rcol:CONFIGS := map {
    "rcol-range": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"/></range></index>
    </collection>,
    "rcol-range-primary": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string" collation="{$rcol:PRIMARY}"/></range></index>
    </collection>,
    "rcol-legacy": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><create qname="w" type="xs:string"/></index>
    </collection>,
    "rcol-field": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create match="//w"><field name="rcol-k" match="@k" type="xs:string"/></create></range></index>
    </collection>,
    "rcol-none": ()
};

declare
    %test:setUp
function rcol:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $name in map:keys($rcol:CONFIGS)
    return (
        if (exists($rcol:CONFIGS($name))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $rcol:CONFIGS($name))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", $rcol:DATA)
    )
};

declare
    %test:tearDown
function rcol:teardown() {
    for $name in map:keys($rcol:CONFIGS)
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare %test:assertEquals(2) function rcol:starts-with-collation-range() {
    count(collection("/db/rcol-range")//w[starts-with(., 'APP', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:contains-collation-range() {
    count(collection("/db/rcol-range")//w[contains(., 'PPL', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:ends-with-collation-range() {
    count(collection("/db/rcol-range")//w[ends-with(., 'PLE', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:starts-with-collation-legacy() {
    count(collection("/db/rcol-legacy")//w[starts-with(., 'APP', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:contains-collation-legacy() {
    count(collection("/db/rcol-legacy")//w[contains(., 'PPL', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:ends-with-collation-legacy() {
    count(collection("/db/rcol-legacy")//w[ends-with(., 'PLE', $rcol:PRIMARY)])
};

declare %test:assertEquals(2) function rcol:starts-with-collation-no-index() {
    count(collection("/db/rcol-none")//w[starts-with(., 'APP', $rcol:PRIMARY)])
};

(: the codepoint collation named explicitly is the index's: the index answers :)
declare %test:assertEquals(1) function rcol:starts-with-codepoint-range() {
    count(collection("/db/rcol-range")//w[starts-with(., 'app', $rcol:CODEPOINT)])
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rcol:starts-with-codepoint-range-uses-index() {
    collection("/db/rcol-range")//w[starts-with(., 'app', $rcol:CODEPOINT)]
};

(: no collation named: a collated index compares with its own collation, by design :)
declare %test:assertEquals(2) function rcol:eq-without-collation-collated-range() {
    count(collection("/db/rcol-range-primary")//w[. = 'APPLE'])
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rcol:eq-without-collation-collated-range-uses-index() {
    collection("/db/rcol-range-primary")//w[. = 'APPLE']
};

(: a field compares by codepoint: the collation is honoured without it :)
declare %test:assertEquals(2) function rcol:starts-with-collation-field() {
    count(collection("/db/rcol-field")//w[starts-with(@k, 'APP', $rcol:PRIMARY)])
};

(: the index's collation, but a substring operator a collated index cannot serve: evaluated as written, no error :)
declare %test:assertEquals(2) function rcol:starts-with-same-collation-collated-range() {
    count(collection("/db/rcol-range-primary")//w[starts-with(., 'APP', $rcol:PRIMARY)])
};

(: equals() with two arguments compares by definition with an identical-strength collation the query does not name:
   the index answers, as for a comparison without a collation :)
declare %test:assertEquals(1) function rcol:equals-without-collation-range() {
    count(collection("/db/rcol-range")//w[equals(., 'Apple')])
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rcol:equals-without-collation-range-uses-index() {
    collection("/db/rcol-range")//w[equals(., 'Apple')]
};

(: a collation named as equals()'s third argument counts :)
declare %test:assertEquals(2) function rcol:equals-collation-range() {
    count(collection("/db/rcol-range")//w[equals(., 'APPLE', $rcol:PRIMARY)])
};
