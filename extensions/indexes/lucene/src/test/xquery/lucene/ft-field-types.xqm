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
(:~
 : Consolidated field-type edge cases (xs:integer/date/dateTime/time + binary-field boolean).
 : Merges #4532, #4533, #4534, #4535, #4536, #4537, #5193 into one XQSuite spec.
 :
 : @see https://github.com/eXist-db/exist/issues/4532
 : @see https://github.com/eXist-db/exist/issues/4533
 : @see https://github.com/eXist-db/exist/issues/4534
 : @see https://github.com/eXist-db/exist/issues/4535
 : @see https://github.com/eXist-db/exist/issues/4536
 : @see https://github.com/eXist-db/exist/issues/4537
 : @see https://github.com/eXist-db/exist/issues/5193
 :)
xquery version "3.1";

module namespace ftft = "http://exist-db.org/xquery/lucene/ft-field-types/test";
declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(:~
 : XQSuite regression test for GitHub #4532: Lucene fields with type xs:integer
 : silently drop values outside the int64 range.
 :
 : Bug: Lucene LongField stores 64-bit signed values. xs:integer in XQuery is
 : unbounded. Values > 9223372036854775807 or < -9223372036854775808 are
 : silently dropped (Long.parseLong throws, convertToField catches and returns
 : null). Expected: error on index or document limitation.
 :
 : @see https://github.com/eXist-db/exist/issues/4532
 :)



(:~
 : Data with in-range integer values (max/min) for indexing.
 :)
declare variable $ftft:COLLECTION_NAME := "lucene-test-ft-field-types";
declare variable $ftft:COLLECTION := "/db/" || $ftft:COLLECTION_NAME;

declare variable $ftft:XCONF_ALL as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                (: #4532 :)
                <text qname="int-in-item">
                    <field name="integer" expression="@integer" type="xs:integer"/>
                </text>
                <text qname="int-out-item">
                    <field name="integer" expression="@integer" type="xs:integer"/>
                </text>

                (: #4533/#4534 :)
                <text qname="date-tz-bin-item">
                    <field name="date-bin" expression="@date" type="xs:date" binary="yes"/>
                </text>
                <text qname="date-year-bin-item">
                    <field name="date-bin" expression="@date" type="xs:date" binary="yes"/>
                </text>
                <text qname="date-nonbin-item">
                    <field name="date-nobin" expression="@date" type="xs:date"/>
                </text>

                (: #4535/#4536/#4537 :)
                <text qname="datetime-item">
                    <field name="dt-bin" expression="@datetime" type="xs:dateTime" binary="yes"/>
                    <field name="tm-bin" expression="@time" type="xs:time" binary="yes"/>
                    <field name="tm-nobin" expression="@time" type="xs:time"/>
                    <field name="dt-nobin" expression="@datetime" type="xs:dateTime"/>
                </text>

                (: #5193 :)
                <text qname="bool-item">
                    <field name="my-field" expression="@flag" type="xs:boolean" binary="yes"/>
                    <field name="from-true" expression="true()" type="xs:boolean" binary="yes"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $ftft:DATA_ALL as document-node() := document {
    <items>
        <int-in-item integer="9223372036854775807"/>
        <int-in-item integer="-9223372036854775808"/>

        <date-tz-bin-item date="2022-08-07">match</date-tz-bin-item>
        <date-tz-bin-item date="2022-08-07Z">match</date-tz-bin-item>
        <date-tz-bin-item date="2022-08-07-02:00">match</date-tz-bin-item>
        <date-tz-bin-item date="2022-08-07+02:00">match</date-tz-bin-item>

        <date-year-bin-item date="-2022-02-22">match</date-year-bin-item>
        <date-year-bin-item date="2022-08-07">match</date-year-bin-item>
        <date-year-bin-item date="131071-08-07">match</date-year-bin-item>
        <date-year-bin-item date="131072-08-07">match</date-year-bin-item>

        <date-nonbin-item date="2022-08-07">match</date-nonbin-item>
        <date-nonbin-item date="2022-08-07Z">match</date-nonbin-item>
        <date-nonbin-item date="2022-08-07-02:00">match</date-nonbin-item>
        <date-nonbin-item date="2022-08-07+02:00">match</date-nonbin-item>

        <datetime-item datetime="2022-08-07T10:34:56.789-02:00" time="10:34:56.789-02:00">match</datetime-item>
        <datetime-item datetime="2022-08-07T14:34:56.789+02:00" time="14:34:56.789+02:00">match</datetime-item>
        <datetime-item datetime="2022-08-07T12:34:56.789Z" time="12:34:56.789Z">match</datetime-item>
        <datetime-item datetime="2022-08-07T12:34:56.789" time="12:34:56.789">match</datetime-item>

        <bool-item flag="true">match</bool-item>
        <bool-item flag="false">match</bool-item>
        <bool-item flag="1">match</bool-item>
        <bool-item flag="0">match</bool-item>
    </items>
};

declare variable $ftft:DATA_INT_OUT as document-node() := document {
    <items>
        <int-out-item integer="9223372036854775808"/>
        <int-out-item integer="-9223372036854775809"/>
    </items>
};

(:~
 : XQSuite setUp: create config chain, store xconf + data, then reindex once.
 :)
declare
    %test:setUp
function ftft:setUp() {
    (
        xmldb:create-collection("/db/system", "config"),
        xmldb:create-collection("/db/system/config", "db"),
        xmldb:create-collection("/db", $ftft:COLLECTION_NAME),
        xmldb:create-collection("/db/system/config/db", $ftft:COLLECTION_NAME),
        xmldb:store("/db/system/config/db/" || $ftft:COLLECTION_NAME, "collection.xconf", $ftft:XCONF_ALL),
        xmldb:store($ftft:COLLECTION, "test.xml", $ftft:DATA_ALL),
        xmldb:reindex($ftft:COLLECTION)
    )
};
(:~
 : XQSuite tearDown: remove data collection and its config subcollection.
 :)
declare
    %test:tearDown
function ftft:tearDown() {
    (
        xmldb:remove($ftft:COLLECTION),
        xmldb:remove("/db/system/config/db/" || $ftft:COLLECTION_NAME)
    )
};
(:~
 : In-range integers: both max and min int64 are indexed and queryable.
 : Index is built once in setUp, then query.
 : @return xs:integer
 :)
declare
    %test:assertEquals(2)
function ftft:in-range-integers-indexed() {
    count(collection($ftft:COLLECTION)//int-in-item[ft:query(., ())])
};

(:~
 : Storing document with out-of-range integers must fail (not silently drop).
 : Storing triggers indexing; expected: xmldb:store throws when index cannot store.
 :)
declare
    %test:assertError("outside long range")
function ftft:out-of-range-integers-store-fails() {
    xmldb:store($ftft:COLLECTION, "out-of-range.xml", $ftft:DATA_INT_OUT)
};

(:~
 : XQSuite regression tests for GitHub #4533 (xs:date timezone dropped) and
 : #4534 (xs:date year limits). Binary field preserves timezone and full year;
 : non-binary LongField loses timezone and has year limits.
 :
 : @see https://github.com/eXist-db/exist/issues/4533
 : @see https://github.com/eXist-db/exist/issues/4534
 :)



(: #4533 binary: all four date values retrievable with timezone preserved :)
declare
    %test:assertEquals(4)
function ftft:binary-timezone-all-indexed() {
    count(collection($ftft:COLLECTION)//date-tz-bin-item[ft:query(., "match")])
};

declare
    %test:assertEquals("2022-08-07", "2022-08-07+02:00", "2022-08-07-02:00", "2022-08-07Z")
function ftft:binary-timezone-values-preserved() {
    (for $hit in collection($ftft:COLLECTION)//date-tz-bin-item[ft:query(., "match")]
    return string(ft:binary-field($hit, "date-bin", "xs:date"))) => sort()
};

(: #4534 binary: all four year values retrievable (including negative and 131072) :)
declare
    %test:assertEquals(4)
function ftft:binary-year-all-indexed() {
    count(collection($ftft:COLLECTION)//date-year-bin-item[ft:query(., "match")])
};

declare
    %test:assertEquals("-2022-02-22", "2022-08-07", "131071-08-07", "131072-08-07")
function ftft:binary-year-values-preserved() {
    for $hit in collection($ftft:COLLECTION)//date-year-bin-item[ft:query(., "match")]
    order by ft:binary-field($hit, "date-bin", "xs:date")
    return string(ft:binary-field($hit, "date-bin", "xs:date"))
};

(: #4533 non-binary: LongField loses timezone; 4 items indexed but values may not preserve TZ :)
declare
    %test:assertEquals(4)
function ftft:nonbinary-timezone-count() {
    count(collection($ftft:COLLECTION)//date-nonbin-item[ft:query(., "match")])
};

(: Non-binary retrieves values – LongField drops timezone so distinct timezone forms may be lost.
   If this fails, non-binary xs:date does not preserve timezone per #4533. :)
declare
    %test:pending("FIXME: non-binary LongField drops timezone per #4533; use binary for date")
    %test:assertEquals("2022-08-07", "2022-08-07+02:00", "2022-08-07-02:00", "2022-08-07Z")
function ftft:nonbinary-timezone-values-preserved() {
    (collection($ftft:COLLECTION)//date-nonbin-item[ft:query(., "match")]
        ! string(ft:field(., "date-nobin", "xs:date")))
    => sort()
};

(:~
 : XQSuite regression tests for GitHub #4535 (xs:time/dateTime normalized to UTC),
 : #4536 (xs:time without timezone uses server TZ), #4537 (ft:field xs:dateTime cast).
 :
 : @see https://github.com/eXist-db/exist/issues/4535
 : @see https://github.com/eXist-db/exist/issues/4536
 : @see https://github.com/eXist-db/exist/issues/4537
 :)



(: #4535: binary datetime and time indexed :)
declare
    %test:assertEquals(4)
function ftft:binary-datetime-count() {
    count(collection($ftft:COLLECTION)//datetime-item[ft:query(., "match")])
};

declare
    %test:assertEquals(4)
function ftft:binary-time-count() {
    count(collection($ftft:COLLECTION)//datetime-item[ft:query(., "match")])
};

(: #4536: xs:time without timezone – normalised against server TZ, not UTC.
   FIXME: Last item (12:34:56.789) uses server TZ; test fails unless TZ=UTC. :)
declare
    %test:pending("FIXME: xs:time without timezone uses server TZ; run with TZ=UTC for reproducibility")
    %test:assertEquals("12:34:56.789Z 12:34:56.789Z 12:34:56.789Z 12:34:56.789Z")
function ftft:time-without-timezone-normalized-to-utc() {
    (collection($ftft:COLLECTION)//datetime-item[ft:query(., "match")]
        ! ft:field(., "tm-nobin", "xs:time"))
    => sort()
    => string-join(" ")
};

(: #4537: Non-binary ft:field(., "datetime", "xs:dateTime") cast fails with
   "xs:dateTime instance must have all fields set". Use binary="yes" + ft:binary-field. :)
declare
    %test:pending("FIXME: non-binary ft:field datetime cast fails; use binary field")
    %test:assertEquals("2022-08-07T12:34:56.789Z", "2022-08-07T12:34:56.789Z", "2022-08-07T12:34:56.789Z", "2022-08-07T12:34:56.789Z")
function ftft:nonbinary-datetime-field-cast() {
    (collection($ftft:COLLECTION)//datetime-item[ft:query(., "match")]
        ! string(ft:field(., "dt-nobin", "xs:dateTime")))
    => sort()
};

(:~
 : XQSuite regression test for GitHub #5193: ft:binary-field does not cast
 : booleans – returns "true"/"false" as xs:string instead of xs:boolean.
 :
 : @see https://github.com/eXist-db/exist/issues/5193
 :)



(: xs:integer for assertEquals - 1=true, 0=false avoids annotation parsing of boolean literals.
   Order by @flag ascending: "0","1","false","true" yields 0,1,0,1 :)
declare
    %test:assertEquals(0, 1, 0, 1)
function ftft:binary-field-returns-boolean() {
    for $hit in collection($ftft:COLLECTION)//bool-item[ft:query(., "match")]
    order by $hit/@flag
    return xs:integer(ft:binary-field($hit, "my-field", "xs:boolean"))
};

declare
    %test:assertTrue
function ftft:binary-field-instance-of-boolean() {
    let $hit := collection($ftft:COLLECTION)//bool-item[ft:query(., "match")][1]
    let $val := ft:binary-field($hit, "my-field", "xs:boolean")
    return $val instance of xs:boolean
};

(: Expression true() from original issue – field indexes constant boolean per item :)
declare
    %test:assertTrue
function ftft:binary-field-from-true-expression() {
    let $hit := collection($ftft:COLLECTION)//bool-item[ft:query(., "match")][1]
    return ft:binary-field($hit, "from-true", "xs:boolean") = true()
};
