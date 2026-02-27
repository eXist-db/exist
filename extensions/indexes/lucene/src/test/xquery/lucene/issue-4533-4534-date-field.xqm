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
 : XQSuite regression tests for GitHub #4533 (xs:date timezone dropped) and
 : #4534 (xs:date year limits). Binary field preserves timezone and full year;
 : non-binary LongField loses timezone and has year limits.
 :
 : @see https://github.com/eXist-db/exist/issues/4533
 : @see https://github.com/eXist-db/exist/issues/4534
 :)
module namespace i4533 = "http://exist-db.org/xquery/lucene/issue-4533-4534/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(: #4533: dates with various timezones :)
declare variable $i4533:DATA_TIMEZONE as document-node() := document {
    <items>
        <item date="2022-08-07">match</item>
        <item date="2022-08-07Z">match</item>
        <item date="2022-08-07-02:00">match</item>
        <item date="2022-08-07+02:00">match</item>
    </items>
};

(: #4534: dates with various year ranges :)
declare variable $i4533:DATA_YEAR as document-node() := document {
    <items>
        <item date="-2022-02-22">match</item>
        <item date="2022-08-07">match</item>
        <item date="131071-08-07">match</item>
        <item date="131072-08-07">match</item>
    </items>
};

declare variable $i4533:XCONF_BINARY as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="item">
                    <field name="date-bin" expression="@date" type="xs:date" binary="yes"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i4533:XCONF_NONBINARY as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="item">
                    <field name="date-nobin" expression="@date" type="xs:date"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i4533:COLL_BINARY_TZ := "i4533-binary-tz";
declare variable $i4533:COLL_BINARY_YEAR := "i4533-binary-year";
declare variable $i4533:COLL_NONBINARY := "i4533-nonbinary";

declare
    %test:setUp
function i4533:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4533:COLL_BINARY_TZ),
      xmldb:create-collection("/db", $i4533:COLL_BINARY_YEAR),
      xmldb:create-collection("/db", $i4533:COLL_NONBINARY),
      xmldb:create-collection("/db/system/config/db", $i4533:COLL_BINARY_TZ),
      xmldb:create-collection("/db/system/config/db", $i4533:COLL_BINARY_YEAR),
      xmldb:create-collection("/db/system/config/db", $i4533:COLL_NONBINARY),
      xmldb:store("/db/system/config/db/" || $i4533:COLL_BINARY_TZ, "collection.xconf", $i4533:XCONF_BINARY),
      xmldb:store("/db/system/config/db/" || $i4533:COLL_BINARY_YEAR, "collection.xconf", $i4533:XCONF_BINARY),
      xmldb:store("/db/system/config/db/" || $i4533:COLL_NONBINARY, "collection.xconf", $i4533:XCONF_NONBINARY),
      xmldb:store("/db/" || $i4533:COLL_BINARY_TZ, "test.xml", $i4533:DATA_TIMEZONE),
      xmldb:store("/db/" || $i4533:COLL_BINARY_YEAR, "test.xml", $i4533:DATA_YEAR),
      xmldb:store("/db/" || $i4533:COLL_NONBINARY, "test.xml", $i4533:DATA_TIMEZONE),
      xmldb:reindex("/db/" || $i4533:COLL_BINARY_TZ),
      xmldb:reindex("/db/" || $i4533:COLL_BINARY_YEAR),
      xmldb:reindex("/db/" || $i4533:COLL_NONBINARY) )
};

declare
    %test:tearDown
function i4533:tearDown() {
    xmldb:remove("/db/" || $i4533:COLL_BINARY_TZ),
    xmldb:remove("/db/" || $i4533:COLL_BINARY_YEAR),
    xmldb:remove("/db/" || $i4533:COLL_NONBINARY),
    xmldb:remove("/db/system/config/db/" || $i4533:COLL_BINARY_TZ),
    xmldb:remove("/db/system/config/db/" || $i4533:COLL_BINARY_YEAR),
    xmldb:remove("/db/system/config/db/" || $i4533:COLL_NONBINARY)
};

(: #4533 binary: all four date values retrievable with timezone preserved :)
declare
    %test:assertEquals(4)
function i4533:binary-timezone-all-indexed() {
    count(collection("/db/" || $i4533:COLL_BINARY_TZ)//item[ft:query(., "match")])
};

declare
    %test:assertEquals("2022-08-07", "2022-08-07+02:00", "2022-08-07-02:00", "2022-08-07Z")
function i4533:binary-timezone-values-preserved() {
    (for $hit in collection("/db/" || $i4533:COLL_BINARY_TZ)//item[ft:query(., "match")]
    return string(ft:binary-field($hit, "date-bin", "xs:date"))) => sort()
};

(: #4534 binary: all four year values retrievable (including negative and 131072) :)
declare
    %test:assertEquals(4)
function i4533:binary-year-all-indexed() {
    count(collection("/db/" || $i4533:COLL_BINARY_YEAR)//item[ft:query(., "match")])
};

declare
    %test:assertEquals("-2022-02-22", "2022-08-07", "131071-08-07", "131072-08-07")
function i4533:binary-year-values-preserved() {
    for $hit in collection("/db/" || $i4533:COLL_BINARY_YEAR)//item[ft:query(., "match")]
    order by ft:binary-field($hit, "date-bin", "xs:date")
    return string(ft:binary-field($hit, "date-bin", "xs:date"))
};

(: #4533 non-binary: LongField loses timezone; 4 items indexed but values may not preserve TZ :)
declare
    %test:assertEquals(4)
function i4533:nonbinary-timezone-count() {
    count(collection("/db/" || $i4533:COLL_NONBINARY)//item[ft:query(., "match")])
};

(: Non-binary retrieves values – LongField drops timezone so distinct timezone forms may be lost.
   If this fails, non-binary xs:date does not preserve timezone per #4533. :)
declare
    %test:pending("FIXME: non-binary LongField drops timezone per #4533; use binary for date")
    %test:assertEquals("2022-08-07", "2022-08-07+02:00", "2022-08-07-02:00", "2022-08-07Z")
function i4533:nonbinary-timezone-values-preserved() {
    (collection("/db/" || $i4533:COLL_NONBINARY)//item[ft:query(., "match")]
        ! string(ft:field(., "date-nobin", "xs:date")))
    => sort()
};
