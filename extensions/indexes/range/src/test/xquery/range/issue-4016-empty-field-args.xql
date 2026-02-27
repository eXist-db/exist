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
 : XQSuite regression test for GitHub #4016: range:field error when combined
 : indexes have empty key arguments (e.g. dataset[@id = ()][@effectiveDate = ()]).
 : Expected: no error; returns empty (nothing matched).
 :
 : @see https://github.com/eXist-db/exist/issues/4016
 :)
module namespace i4016 = "http://exist-db.org/xquery/range/issue-4016/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace range = "http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";

declare variable $i4016:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <range>
                <create qname="@id" type="xs:string"/>
                <create qname="@effectiveDate" type="xs:string"/>
                <create qname="dataset">
                    <field name="dataset-id" match="@id" type="xs:string"/>
                    <field name="dataset-effectiveDate" match="@effectiveDate" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $i4016:DATA :=
    <decor>
        <datasets>
            <dataset id="2.16.840.1.113883.3.1937.99.62.3.1.1" effectiveDate="2012-05-30T11:32:36">
                <name language="en-US">Demo dataset</name>
            </dataset>
        </datasets>
    </decor>;

declare variable $i4016:COLLECTION := "i4016";

declare
    %test:setUp
function i4016:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4016:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4016:COLLECTION),
      xmldb:store("/db/system/config/db/" || $i4016:COLLECTION, "collection.xconf", $i4016:XCONF),
      xmldb:store("/db/" || $i4016:COLLECTION, "decor.xml", $i4016:DATA),
      xmldb:reindex("/db/" || $i4016:COLLECTION) )
};

declare
    %test:tearDown
function i4016:tearDown() {
    xmldb:remove("/db/" || $i4016:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i4016:COLLECTION)
};

(: #4016: Empty key variables – must not throw XPTY0004; returns empty. :)
declare
    %test:assertEquals(0)
function i4016:empty-keys-no-error() {
    let $id := ()
    let $effectiveDate := ()
    return count(collection("/db/" || $i4016:COLLECTION)//dataset[@id = $id][@effectiveDate = $effectiveDate])
};

(: Non-empty keys – should find 1 match (sanity check). :)
declare
    %test:assertEquals(1)
function i4016:non-empty-keys-match() {
    let $id := "2.16.840.1.113883.3.1937.99.62.3.1.1"
    let $effectiveDate := "2012-05-30T11:32:36"
    return count(collection("/db/" || $i4016:COLLECTION)//dataset[@id = $id][@effectiveDate = $effectiveDate])
};

(: Direct range:field-eq with empty keys – must not throw. :)
declare
    %test:assertEquals(0)
function i4016:explicit-empty-field-eq() {
    count(collection("/db/" || $i4016:COLLECTION)//range:field-eq(("dataset-id", "dataset-effectiveDate"), (), ()))
};
