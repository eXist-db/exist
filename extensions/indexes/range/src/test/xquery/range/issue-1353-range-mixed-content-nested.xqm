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
 : XQSuite regression test for GitHub #1353: range index on mixed content
 : with nested field. Bug: text after first nested element not indexed.
 : def[contains(.,'abc')] works; def[contains(.,'ghi')] fails (ghi is after <x/>).
 :
 : @see https://github.com/eXist-db/exist/issues/1353
 :)
module namespace i1353 = "http://exist-db.org/xquery/range/issue-1353/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $i1353:COLLECTION := "i1353-mixed-content";

declare variable $i1353:DATA as document-node() :=
    document {
        <root>
            <def> abc <x>def</x> ghi </def>
            <def> jklmnopqr </def>
        </root>
    };

(: Nested field on def – bug: indexes only up to first nested element. :)
declare variable $i1353:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="def">
                    <field name="textcontent" type="xs:string" case="no" nested="yes"/>
                </create>
            </range>
        </index>
    </collection>;

declare
    %test:setUp
function i1353:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i1353:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i1353:COLLECTION),
      xmldb:store("/db/" || $i1353:COLLECTION, "debug.xml", $i1353:DATA),
      xmldb:store("/db/system/config/db/" || $i1353:COLLECTION, "collection.xconf", $i1353:XCONF),
      xmldb:reindex("/db/" || $i1353:COLLECTION) )
};

declare
    %test:tearDown
function i1353:tearDown() {
    xmldb:remove("/db/" || $i1353:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i1353:COLLECTION)
};

(: contains(.,'abc') – text before nested element; works. :)
declare
    %test:assertEquals(1)
function i1353:contains-abc-works() {
    count(collection("/db/" || $i1353:COLLECTION)//def[contains(., 'abc')])
};

(: contains(.,'abc def') – spans across nested; works. :)
declare
    %test:assertEquals(1)
function i1353:contains-abc-def-works() {
    count(collection("/db/" || $i1353:COLLECTION)//def[contains(., 'abc def')])
};

(: contains(.,'ghi') – text after nested element; fixed in #1353. :)
declare
    %test:assertEquals(1)
function i1353:contains-ghi() {
    count(collection("/db/" || $i1353:COLLECTION)//def[contains(., 'ghi')])
};

(: contains(.,'def ghi') – spans nested to after; fixed in #1353. :)
declare
    %test:assertEquals(1)
function i1353:contains-def-ghi() {
    count(collection("/db/" || $i1353:COLLECTION)//def[contains(., 'def ghi')])
};
