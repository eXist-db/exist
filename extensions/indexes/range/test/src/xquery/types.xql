xquery version "3.0";

module namespace tt="http://exist-db.org/xquery/range/types/test";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $tt:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:tei="http://www.tei-c.org/ns/1.0">
            <fulltext default="none" attributes="false"/>
            <range>
                <create qname="date" type="xs:date"/>
                <create qname="time" type="xs:time"/>
                <create qname="dateTime" type="xs:dateTime"/>
                <create qname="entry">
                    <field name="date" match="date2" type="xs:date"/>
                </create>
                <create qname="string-lc" type="xs:string" case="no"/>
                <create qname="string" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $tt:XML :=
    <test>
        <entry>
            <id>E1</id>
            <date>1918-02-11</date>
            <date2>1918-02-11</date2>
            <time>09:00:00Z</time>
            <dateTime>1918-02-11T09:00:00Z</dateTime>
            <string-lc>UPPERCASE</string-lc>
            <string>UPPERCASE</string>
        </entry>
        <entry>
            <id>E2</id>
            <date>2012-01-20</date>
            <date2>2012-01-20</date2>
            <time>10:00:00Z</time>
            <dateTime>2012-01-20T10:00:00Z</dateTime>
            <string-lc>lowercase</string-lc>
            <string>lowercase</string>
        </entry>
        <entry>
            <id>E3</id>
            <date>2013-02-04</date>
            <date2>2013-02-04</date2>
            <time>10:00:00+01:00</time>
            <dateTime>2012-01-20T11:00:00+01:00</dateTime>
            <string-lc>MiXeDmOdE</string-lc>
            <string>MiXeDmOdE</string>
        </entry>
    </test>;

declare variable $tt:COLLECTION_NAME := "typestest";
declare variable $tt:COLLECTION := "/db/" || $tt:COLLECTION_NAME;

declare
    %test:setUp
function tt:setup() {
    xmldb:create-collection("/db/system/config/db", $tt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $tt:COLLECTION_NAME, "collection.xconf", $tt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $tt:COLLECTION_NAME),
    xmldb:store($tt:COLLECTION, "test.xml", $tt:XML)
};

declare
    %test:tearDown
function tt:cleanup() {
    xmldb:remove($tt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $tt:COLLECTION_NAME)
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("<id>E2</id>")
    %test:args("1918-02-11")
    %test:assertEquals("<id>E1</id>")
function tt:eq-date($date as xs:date) {
    collection($tt:COLLECTION)//entry[date = $date]/id
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:eq-date-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date = $date]
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("<id>E2</id>")
    %test:args("1918-02-11")
    %test:assertEquals("<id>E1</id>")
function tt:eq-date-field($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 = $date]/id
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:eq-date-field-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 = $date]
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("E3")
    %test:args("1900-01-01")
    %test:assertEquals("E1", "E2", "E3")
    %test:args("2013-12-24")
    %test:assertEmpty
function tt:gt-date($date as xs:date) {
    collection($tt:COLLECTION)//entry[date > $date]/id/string()
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:gt-date-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date > $date]/id/string()
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("E3")
    %test:args("1900-01-01")
    %test:assertEquals("E1", "E2", "E3")
    %test:args("2013-12-24")
    %test:assertEmpty
function tt:gt-date-field($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 > $date]/id/string()
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:gt-date-field-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 > $date]/id/string()
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("E1")
    %test:args("1900-01-01")
    %test:assertEmpty
    %test:args("2013-12-24")
    %test:assertEquals("E1", "E2", "E3")
function tt:lt-date($date as xs:date) {
    collection($tt:COLLECTION)//entry[date < $date]/id/string()
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:lt-date-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date < $date]/id/string()
};

declare 
    %test:args("2012-01-20")
    %test:assertEquals("E1")
    %test:args("1900-01-01")
    %test:assertEmpty
    %test:args("2013-12-24")
    %test:assertEquals("E1", "E2", "E3")
function tt:lt-date-field($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 < $date]/id/string()
};

declare 
    %test:stats
    %test:args("2012-01-20")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:lt-date-field-optimize($date as xs:date) {
    collection($tt:COLLECTION)//entry[date2 < $date]/id/string()
};

declare 
    %test:args("09:00:00Z")
    %test:assertEquals("E1", "E3")
    %test:args("10:00:00Z")
    %test:assertEquals("E2")
function tt:eq-time($time as xs:time) {
    collection($tt:COLLECTION)//entry[time = $time]/id/string()
};

declare 
    %test:stats
    %test:args("10:00:00Z")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:eq-time-optimize($time as xs:time) {
    collection($tt:COLLECTION)//entry[time = $time]/id/string()
};

declare 
    %test:args("09:00:00Z")
    %test:assertEmpty
    %test:args("10:00:00Z")
    %test:assertEquals("E1", "E3")
function tt:lt-time($time as xs:time) {
    collection($tt:COLLECTION)//entry[time < $time]/id/string()
};

declare 
    %test:args("09:00:00Z")
    %test:assertEquals("E2")
    %test:args("10:00:00Z")
    %test:assertEmpty
function tt:gt-time($time as xs:time) {
    collection($tt:COLLECTION)//entry[time > $time]/id/string()
};

declare 
    %test:args("2012-01-20T10:00:00Z")
    %test:assertEquals("E2", "E3")
function tt:eq-dateTime($dateTime as xs:dateTime) {
    collection($tt:COLLECTION)//entry[dateTime = $dateTime]/id/string()
};

declare 
    %test:stats
    %test:args("2012-01-20T10:00:00Z")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:eq-dateTime-optimize($dateTime as xs:dateTime) {
    collection($tt:COLLECTION)//entry[dateTime = $dateTime]/id/string()
};

declare 
    %test:args("1918-02-11T09:00:00Z")
    %test:assertEquals("E2", "E3")
    %test:args("2012-01-20T10:00:00Z")
    %test:assertEmpty
function tt:gt-dateTime($dateTime as xs:dateTime) {
    collection($tt:COLLECTION)//entry[dateTime > $dateTime]/id/string()
};

declare 
    %test:args("1918-02-11T09:00:00Z")
    %test:assertEmpty
    %test:args("2012-01-20T10:00:00Z")
    %test:assertEquals("E1")
function tt:lt-dateTime($dateTime as xs:dateTime) {
    collection($tt:COLLECTION)//entry[dateTime < $dateTime]/id/string()
};

declare
    %test:stats
    %test:args("up")
    %test:assertXPath("$result//stats:index[@type = 'new-range'][@optimization = 2]")
function tt:string-contains-optimized($string as xs:string) {
    collection($tt:COLLECTION)//entry[contains(string, $string)]/id/string()
};
    
declare
    %test:args("up")
    %test:assertEquals("E1")
    %test:args("UP")
    %test:assertEquals("E1")
    %test:args("Up")
    %test:assertEquals("E1")
    %test:args("case")
    %test:assertEquals("E1", "E2")
    %test:args("mixed")
    %test:assertEquals("E3")
    %test:args("MIXED")
    %test:assertEquals("E3")
    %test:args("MiXeD")
    %test:assertEquals("E3")
function tt:string-contains-ignore-case($string as xs:string) {
    collection($tt:COLLECTION)//entry[contains(string-lc, $string)]/id/string()
};

declare 
    %test:args("mixedmode")
    %test:assertEquals("E3")
    %test:args("MIXEDMODE")
    %test:assertEquals("E3")
    %test:args("MiXeDmOdE")
    %test:assertEquals("E3")
function tt:string-equals-ignore-case($string as xs:string) {
    collection($tt:COLLECTION)//entry[string-lc eq $string]/id/string()
};

declare 
    %test:args("mixedmode")
    %test:assertEquals("E3")
    %test:args("MIXEDMODE")
    %test:assertEquals("E3")
    %test:args("MiXeDmOdE")
    %test:assertEquals("E3")
function tt:string-equals-2-ignore-case($string as xs:string) {
    collection($tt:COLLECTION)//entry[string-lc = $string]/id/string()
};

declare 
    %test:args("up")
    %test:assertEquals("E1")
    %test:args("UP")
    %test:assertEquals("E1")
    %test:args("Up")
    %test:assertEquals("E1")
    %test:args("case")
    %test:assertEmpty
    %test:args("mixed")
    %test:assertEquals("E3")
    %test:args("MIXED")
    %test:assertEquals("E3")
    %test:args("MiXeD")
    %test:assertEquals("E3")
function tt:starts-with-ignore-case($string as xs:string) {
    collection($tt:COLLECTION)//entry[starts-with(string-lc, $string)]/id/string()
};

declare 
    %test:args("percase")
    %test:assertEquals("E1")
    %test:args("PeRcAsE")
    %test:assertEquals("E1")
    %test:args("PERCASE")
    %test:assertEquals("E1")
    %test:args("cAsE")
    %test:assertEquals("E1", "E2")
function tt:ends-with-ignore-case($string as xs:string) {
    collection($tt:COLLECTION)//entry[ends-with(string-lc, $string)]/id/string()
};

declare
    %test:args("lo")
    %test:assertEquals("E2")
    %test:args("UP")
    %test:assertEquals("E1")
    %test:args("Up")
    %test:assertEmpty
    %test:args("case")
    %test:assertEquals("E2")
    %test:args("mixed")
    %test:assertEmpty
    %test:args("MIXED")
    %test:assertEmpty
    %test:args("MiXeD")
    %test:assertEquals("E3")
function tt:string-contains($string as xs:string) {
    collection($tt:COLLECTION)//entry[contains(string, $string)]/id/string()
};

declare 
    %test:args("mixedmode")
    %test:assertEmpty
    %test:args("MIXEDMODE")
    %test:assertEmpty
    %test:args("MiXeDmOdE")
    %test:assertEquals("E3")
function tt:string-equals($string as xs:string) {
    collection($tt:COLLECTION)//entry[string eq $string]/id/string()
};

declare 
    %test:args("mixedmode")
    %test:assertEmpty
    %test:args("MIXEDMODE")
    %test:assertEmpty
    %test:args("MiXeDmOdE")
    %test:assertEquals("E3")
function tt:string-equals-2($string as xs:string) {
    collection($tt:COLLECTION)//entry[string = $string]/id/string()
};

declare 
    %test:args("up")
    %test:assertEmpty
    %test:args("UP")
    %test:assertEquals("E1")
    %test:args("Up")
    %test:assertEquals
    %test:args("case")
    %test:assertEmpty
    %test:args("mixed")
    %test:assertEmpty
    %test:args("MIXED")
    %test:assertEmpty
    %test:args("MiXeD")
    %test:assertEquals("E3")
function tt:starts-with($string as xs:string) {
    collection($tt:COLLECTION)//entry[starts-with(string, $string)]/id/string()
};

declare 
    %test:args("percase")
    %test:assertEmpty
    %test:args("PeRcAsE")
    %test:assertEmpty
    %test:args("PERCASE")
    %test:assertEquals("E1")
    %test:args("cAsE")
    %test:assertEmpty
    %test:args("case")
    %test:assertEquals("E2")
function tt:ends-with($string as xs:string) {
    collection($tt:COLLECTION)//entry[ends-with(string, $string)]/id/string()
};
