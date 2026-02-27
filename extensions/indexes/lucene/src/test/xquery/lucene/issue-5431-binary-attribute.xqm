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
 : XQSuite regression test for GitHub #5431: binary attribute in Lucene
 : full-text fields does not work anymore. With binary="yes", ft:binary-field
 : should return indexed values for sorting/retrieval.
 :
 : @see https://github.com/eXist-db/exist/issues/5431
 :)
module namespace i5431 = "http://exist-db.org/xquery/lucene/issue-5431/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

(: Content "match" for search; @sortable for binary field (same pattern as #5193). :)
declare variable $i5431:DATA as document-node() := document {
    <div>
        <test sortable="Adm. 1,10">match</test>
        <test sortable="Bdm. 1,11">match</test>
        <test sortable="Cdm. 1,12">match</test>
        <test sortable="Edm. 1,1">match</test>
        <test sortable="Fdm. 1,2">match</test>
        <test sortable="Gdm. 1,3">match</test>
        <test sortable="Zdm. 1,4">match</test>
        <test sortable="Wdm. 1,5">match</test>
        <test sortable="Odm. 1,6">match</test>
        <test sortable="Ydm. 1,7">match</test>
        <test sortable="Cdm. 1,8">match</test>
        <test sortable="Vdm. 1,9">match</test>
        <test sortable="Pdm. 1,13">match</test>
        <test sortable="Edm. 1,14">match</test>
    </div>
};

declare variable $i5431:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="test">
                    <field name="sortable" expression="./@sortable/string()" type="xs:string" binary="yes"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i5431:COLLECTION_NAME := "i5431-binary";
declare variable $i5431:COLLECTION := "/db/" || $i5431:COLLECTION_NAME;

declare
    %test:setUp
function i5431:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i5431:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i5431:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i5431:COLLECTION_NAME, "collection.xconf", $i5431:XCONF),
      xmldb:store($i5431:COLLECTION, "test.xml", $i5431:DATA),
      xmldb:reindex($i5431:COLLECTION) )
};

declare
    %test:tearDown
function i5431:tearDown() {
    xmldb:remove($i5431:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i5431:COLLECTION_NAME)
};

(: #5431: ft:binary-field returns values when binary="yes" on string field. :)
declare
    %test:assertTrue
function i5431:binary-field-returns-values() {
    let $hits := collection($i5431:COLLECTION)//test[ft:query(., "match")]
    return $hits[1] ! (count(ft:binary-field(., "sortable", "xs:string")) gt 0)
};

(: #5431: sort by binary field yields correct order. :)
declare
    %test:assertEquals("Adm. 1,10", "Bdm. 1,11", "Cdm. 1,12", "Cdm. 1,8", "Edm. 1,1", "Edm. 1,14", "Fdm. 1,2", "Gdm. 1,3", "Odm. 1,6", "Pdm. 1,13", "Vdm. 1,9", "Wdm. 1,5", "Ydm. 1,7", "Zdm. 1,4")
function i5431:sorted-by-binary-field() {
    for $hit in collection($i5431:COLLECTION)//test[ft:query(., "match")]
    order by ft:binary-field($hit, "sortable", "xs:string") ascending
    return string(ft:binary-field($hit, "sortable", "xs:string"))
};
