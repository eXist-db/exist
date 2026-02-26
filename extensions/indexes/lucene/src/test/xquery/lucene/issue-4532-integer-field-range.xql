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
module namespace i4532 = "http://exist-db.org/xquery/lucene/issue-4532/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(:~
 : Data with 4 items: 2 in int64 range (max/min), 2 out of range (max+1, min-1).
 : Out-of-range values are silently dropped in current implementation.
 :)
declare variable $i4532:DATA_OUT_OF_RANGE as document-node() := document {
    <items>
        <item integer="9223372036854775807"/>
        <item integer="9223372036854775808"/>
        <item integer="-9223372036854775808"/>
        <item integer="-9223372036854775809"/>
    </items>
};

(:~ Data with only in-range integers (sanity check). :)
declare variable $i4532:DATA_IN_RANGE as document-node() := document {
    <items>
        <item integer="9223372036854775807"/>
        <item integer="-9223372036854775808"/>
    </items>
};

(:~
 : Lucene config: xs:integer field on item/@integer.
 : @return element(collection)
 :)
declare variable $i4532:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="item">
                    <field name="integer" expression="@integer" type="xs:integer"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i4532:COLLECTION_OUT := "issue4532-out";
declare variable $i4532:COLLECTION_IN := "issue4532-in";

(:~
 : XQSuite setUp: create config chain, both collections, store in-range doc and xconf.
 : Do not store out-of-range doc – storing triggers indexing which throws.
 :)
declare
    %test:setUp
function i4532:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4532:COLLECTION_OUT),
      xmldb:create-collection("/db", $i4532:COLLECTION_IN),
      xmldb:create-collection("/db/system/config/db", $i4532:COLLECTION_OUT),
      xmldb:create-collection("/db/system/config/db", $i4532:COLLECTION_IN),
      xmldb:store("/db/system/config/db/" || $i4532:COLLECTION_OUT, "collection.xconf", $i4532:XCONF),
      xmldb:store("/db/system/config/db/" || $i4532:COLLECTION_IN, "collection.xconf", $i4532:XCONF),
      xmldb:store("/db/" || $i4532:COLLECTION_IN, "in-range.xml", $i4532:DATA_IN_RANGE) )
};

(:~
 : XQSuite tearDown: remove both test collections and their configs.
 :)
declare
    %test:tearDown
function i4532:tearDown() {
    xmldb:remove("/db/" || $i4532:COLLECTION_OUT),
    xmldb:remove("/db/" || $i4532:COLLECTION_IN),
    xmldb:remove("/db/system/config/db/" || $i4532:COLLECTION_OUT),
    xmldb:remove("/db/system/config/db/" || $i4532:COLLECTION_IN)
};

(:~
 : In-range integers: both max and min int64 are indexed and queryable.
 : Reindex in-range collection (setUp does not reindex), then query.
 : @return xs:integer
 :)
declare
    %test:assertEquals(2)
function i4532:in-range-integers-indexed() {
    let $_ := xmldb:reindex("/db/" || $i4532:COLLECTION_IN)
    return count(collection("/db/" || $i4532:COLLECTION_IN)//item[ft:query(., ())])
};

(:~
 : Storing document with out-of-range integers must fail (not silently drop).
 : Storing triggers indexing; expected: xmldb:store throws when index cannot store.
 :)
declare
    %test:assertError
function i4532:out-of-range-integers-store-fails() {
    xmldb:store("/db/" || $i4532:COLLECTION_OUT, "out-of-range.xml", $i4532:DATA_OUT_OF_RANGE)
};
