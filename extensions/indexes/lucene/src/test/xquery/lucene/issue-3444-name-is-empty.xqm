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
 : XQSuite regression test for GitHub #3444: ft:query throws "name is empty"
 : when collection has Lucene index with field name (e.g. name="de") that is
 : also a valid hex string. getDefinedIndexesFor decodes all Lucene field names;
 : custom field "de" decodes to a QName with empty local part, causing
 : encodeQName to call SymbolTable.getSymbol("") → "name is empty".
 :
 : Config from myapp-0.1: tei:person with field name="de" expression="*[@xml:lang='de']".
 : Query: let $base := ...//tei:person; ft:query($base, 'de:a*')
 :
 : @see https://github.com/eXist-db/exist/issues/3444
 :)
module namespace i3444 = "http://exist-db.org/xquery/lucene/issue-3444/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace tei = "http://www.tei-c.org/ns/1.0";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(: Minimal TEI data with person elements; some have xml:lang="de" content (e.g. persName). :)
declare variable $i3444:DATA as document-node() :=
    document {
        <TEI xmlns="http://www.tei-c.org/ns/1.0">
            <listPerson>
                <person xml:id="p1">
                    <persName xml:lang="de">Anna Berger</persName>
                    <persName xml:lang="fra">Anne Berger</persName>
                </person>
                <person xml:id="p2">
                    <persName xml:lang="de">Bert Ackermann</persName>
                    <persName xml:lang="fra">Bertrand Ackermann</persName>
                </person>
                <person xml:id="p3">
                    <persName xml:lang="fra">Claude Dupont</persName>
                </person>
            </listPerson>
        </TEI>
    };

(: Same index config as myapp: tei:person with field name="de" on *[@xml:lang='de']. :)
declare variable $i3444:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:tei="http://www.tei-c.org/ns/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="tei:person" index="no">
                    <field name="de" expression="*[@xml:lang='de']"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i3444:COLLECTION := "i3444";

declare
    %test:setUp
function i3444:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i3444:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i3444:COLLECTION),
      xmldb:store("/db/system/config/db/" || $i3444:COLLECTION, "collection.xconf", $i3444:XCONF),
      xmldb:store("/db/" || $i3444:COLLECTION, "data.xml", $i3444:DATA),
      xmldb:reindex("/db/" || $i3444:COLLECTION) )
};

declare
    %test:tearDown
function i3444:tearDown() {
    xmldb:remove("/db/" || $i3444:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i3444:COLLECTION)
};

(: #3444: Original failing pattern – let $base then ft:query($base, 'de:a*'). Must not throw "name is empty". :)
declare
    %test:assertTrue
function i3444:query-indirect-no-exception() {
    let $base := doc("/db/" || $i3444:COLLECTION || "/data.xml")//tei:person
    let $hits := $base[ft:query(., "de:a*", map { "leading-wildcard": "yes" })]
    return true()
};

(: Inline form – must return hits (Anna Berger, Bert Ackermann match de:a*). :)
declare
    %test:assertEquals(2)
function i3444:query-inline-field-prefix() {
    count(
        doc("/db/" || $i3444:COLLECTION || "/data.xml")//tei:person[
            ft:query(., "de:a*", map { "leading-wildcard": "yes" })
        ]
    )
};
