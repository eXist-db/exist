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
 : A default collation the range index cannot honour: the index answers a comparison only when it compares in the
 : query's default collation, and otherwise the comparison is evaluated as written. The legacy index is given the
 : default collation. Expected counts follow the spec: with a primary-strength collation, 'Apple' and 'apple' both
 : equal 'APPLE'.
 :)
module namespace rcold="http://exist-db.org/xquery/range/collation-default/test";

declare default collation "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $rcold:DATA := <doc><w k="Apple">Apple</w><w k="apple">apple</w><w k="Banana">Banana</w></doc>;

(: collection name -> index configuration; "none" has no configuration :)
declare variable $rcold:CONFIGS := map {
    "rcold-range": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"/></range></index>
    </collection>,
    "rcold-range-primary": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"
            collation="http://www.w3.org/2013/collation/UCA?lang=en;strength=primary"/></range></index>
    </collection>,
    "rcold-range-tertiary": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"
            collation="http://www.w3.org/2013/collation/UCA?lang=en;strength=tertiary"/></range></index>
    </collection>,
    "rcold-legacy": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><create qname="w" type="xs:string"/></index>
    </collection>,
    "rcold-field": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create match="//w"><field name="rcold-k" match="@k" type="xs:string"/></create></range></index>
    </collection>,
    "rcold-field-primary": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create match="//w"
            collation="http://www.w3.org/2013/collation/UCA?lang=en;strength=primary"><field name="rcold-kp" match="@k" type="xs:string"/></create></range></index>
    </collection>,
    "rcold-none": ()
};

declare
    %test:setUp
function rcold:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $name in map:keys($rcold:CONFIGS)
    return (
        if (exists($rcold:CONFIGS($name))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $rcold:CONFIGS($name))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", $rcold:DATA)
    )
};

declare
    %test:tearDown
function rcold:teardown() {
    for $name in map:keys($rcold:CONFIGS)
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare %test:assertEquals(2) function rcold:eq-codepoint-range() {
    count(collection("/db/rcold-range")//w[. = 'APPLE'])
};

declare %test:assertEquals(2) function rcold:eq-legacy() {
    count(collection("/db/rcold-legacy")//w[. = 'APPLE'])
};

(: a string function takes the default collation too: the legacy index matches its keys with it :)
declare %test:assertEquals(2) function rcold:starts-with-legacy() {
    count(collection("/db/rcold-legacy")//w[starts-with(., 'APP')])
};

(: a field is indexed in the collation of its configuration: with the default collation the same, the field answers :)
declare %test:assertEquals(2) function rcold:eq-field-same-collation() {
    count(collection("/db/rcold-field-primary")//w[@k = 'APPLE'])
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rcold:eq-field-same-collation-uses-index() {
    collection("/db/rcold-field-primary")//w[@k = 'APPLE']
};

declare %test:assertEquals(2) function rcold:eq-no-index() {
    count(collection("/db/rcold-none")//w[. = 'APPLE'])
};

declare %test:assertEquals(2) function rcold:eq-other-collation-range() {
    count(collection("/db/rcold-range-tertiary")//w[. = 'APPLE'])
};

(: the index compares in the default collation: it answers :)
declare %test:assertEquals(2) function rcold:eq-same-collation-range() {
    count(collection("/db/rcold-range-primary")//w[. = 'APPLE'])
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rcold:eq-same-collation-range-uses-index() {
    collection("/db/rcold-range-primary")//w[. = 'APPLE']
};

declare %test:assertEquals(2) function rcold:eq-field() {
    count(collection("/db/rcold-field")//w[@k = 'APPLE'])
};

declare %test:assertEquals(2) function rcold:value-eq-codepoint-range() {
    count(collection("/db/rcold-range")//w[. eq 'APPLE'])
};

declare %test:assertEquals(2) function rcold:value-eq-legacy() {
    count(collection("/db/rcold-legacy")//w[. eq 'APPLE'])
};

(: by the collation, Apple and apple sort before BANANA; by codepoint, only Apple does :)
declare %test:assertEquals(2) function rcold:lt-codepoint-range() {
    count(collection("/db/rcold-range")//w[. < 'BANANA'])
};

declare %test:assertEquals(2) function rcold:lt-legacy() {
    count(collection("/db/rcold-legacy")//w[. < 'BANANA'])
};

(: one collection's index compares in the collation, the other's does not: the answer covers both :)
declare %test:assertEquals(4) function rcold:eq-collections-with-matching-and-other-index() {
    count((collection("/db/rcold-range-primary"), collection("/db/rcold-range"))//w[. = 'APPLE'])
};

(: a where clause is evaluated in one step over the for clause's nodes, like a predicate :)
declare %test:assertEquals(2) function rcold:where-legacy() {
    count(for $w in collection("/db/rcold-legacy")//w where $w = 'APPLE' return $w)
};

declare %test:assertEquals(2) function rcold:where-codepoint-range() {
    count(for $w in collection("/db/rcold-range")//w where $w = 'APPLE' return $w)
};

(: in-memory nodes: no index, the comparison is evaluated as written :)
declare %test:assertEquals(2) function rcold:where-in-memory() {
    count(for $w in $rcold:DATA/w where $w = 'APPLE' return $w)
};

declare %test:assertEquals(2) function rcold:where-in-memory-text() {
    count(for $w in $rcold:DATA/w where $w/text() = 'APPLE' return $w)
};
