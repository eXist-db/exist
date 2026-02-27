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
 : XQSuite regression test for GitHub #3114: attribute range index produces
 : incorrect results when used with ft:query. The predicate [@attr="value"]
 : is ignored when a range index is defined on @attr; the expression behaves
 : like ft:query(.//field, ...) instead of ft:query(.//field[@attr="value"], ...).
 :
 : Lucene+range interaction – test lives in Range module (has both indexes).
 : Uses two collections (no-range vs with-range) to assert identical results;
 : the bug surfaces only when the range index exists.
 :
 : @see https://github.com/eXist-db/exist/issues/3114
 :)
module namespace i3114 = "http://exist-db.org/xquery/range/issue-3114/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace ft = "http://exist-db.org/xquery/lucene";

(: Col1: Lucene only. Col2: Lucene + range on field/@name (triggers #3114 when buggy). :)
declare variable $i3114:DATA as document-node() := document {
    <root>
        <record id="1">
            <field name="name">Named Artist</field>
            <field name="desc">Artist #1</field>
        </record>
        <record id="2">
            <field name="name">Someone else</field>
            <field name="desc">Artist #2</field>
        </record>
    </root>
};

declare variable $i3114:FTQUERY as element(phrase) := <phrase>Artist</phrase>;

declare variable $i3114:XCONF_NO_RANGE as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="field"/>
            </lucene>
        </index>
    </collection>;

declare variable $i3114:XCONF_WITH_RANGE as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="field"/>
            </lucene>
            <create qname="@name" type="xs:string"/>
        </index>
    </collection>;

declare variable $i3114:COLL_NO_RANGE := "i3114-no-range";
declare variable $i3114:COLL_WITH_RANGE := "i3114-with-range";

declare
    %test:setUp
function i3114:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3114:COLL_NO_RANGE),
      xmldb:create-collection("/db/system/config/db", $i3114:COLL_NO_RANGE),
      xmldb:create-collection("/db", $i3114:COLL_WITH_RANGE),
      xmldb:create-collection("/db/system/config/db", $i3114:COLL_WITH_RANGE),
      xmldb:store("/db/system/config/db/" || $i3114:COLL_NO_RANGE, "collection.xconf", $i3114:XCONF_NO_RANGE),
      xmldb:store("/db/" || $i3114:COLL_NO_RANGE, "data.xml", $i3114:DATA),
      xmldb:store("/db/system/config/db/" || $i3114:COLL_WITH_RANGE, "collection.xconf", $i3114:XCONF_WITH_RANGE),
      xmldb:store("/db/" || $i3114:COLL_WITH_RANGE, "data.xml", $i3114:DATA),
      xmldb:reindex("/db/" || $i3114:COLL_NO_RANGE),
      xmldb:reindex("/db/" || $i3114:COLL_WITH_RANGE) )
};

declare
    %test:tearDown
function i3114:tearDown() {
    xmldb:remove("/db/" || $i3114:COLL_NO_RANGE),
    xmldb:remove("/db/system/config/db/" || $i3114:COLL_NO_RANGE),
    xmldb:remove("/db/" || $i3114:COLL_WITH_RANGE),
    xmldb:remove("/db/system/config/db/" || $i3114:COLL_WITH_RANGE)
};

(: #3114: Only 1 field[@name='name'] matches "Artist". Both configs must return 1. :)
declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function i3114:query-name-field($col as xs:string) {
    count(collection($col)/ft:query(.//field[@name = "name"], $i3114:FTQUERY))
};

(: Alternate: field[@name][ft:query(., phrase)] – predicate before ft:query, not affected by bug. :)
declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function i3114:query-name-field-alternate($col as xs:string) {
    count(collection($col)//record/field[@name = "name"][ft:query(., $i3114:FTQUERY)])
};

(: #3114 BUG PATTERN: record[ft:query(.//field[@name='name'], phrase)] – predicate ignored with range index. :)
declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function i3114:query-record-with-name-field($col as xs:string) {
    count(collection($col)//record[ft:query(.//field[@name = "name"], $i3114:FTQUERY)])
};

(: Workaround: record[.//field[@name][ft:query(., phrase)]] – predicate before ft:query. :)
declare
    %test:arg("col", "/db/i3114-no-range") %test:assertEquals(1)
    %test:arg("col", "/db/i3114-with-range") %test:assertEquals(1)
function i3114:query-record-with-name-field-alternate($col as xs:string) {
    count(collection($col)//record[.//field[@name = "name"][ft:query(., $i3114:FTQUERY)]])
};
