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
xquery version "3.0";

module namespace qf="http://exist-db.org/xquery/lucene/test/query-field";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $qf:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene diacritics="no">
                <text field="testField" qname="test"/>
            </lucene>
        </index>
    </collection>;

declare variable $qf:XML  as document-node() :=
     document {
        <test>
            <p>Rüsselsheim</p>
            <p>Russelsheim</p>
            <p>Māori</p>
            <p>Maori</p>
        </test>
     };

(: Create config hierarchy in setup; plain-ft-functions removes /db/system/config/db so we must recreate. :)
declare variable $qf:config-db-path := "/db/system/config/db";
declare variable $qf:test-coll-name := "lucene-test-query-field";
declare variable $qf:conf-coll-path := $qf:config-db-path || "/" || $qf:test-coll-name;

declare
%test:setUp
function qf:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $confCol := xmldb:create-collection($qf:config-db-path, $qf:test-coll-name)
    let $testCol := xmldb:create-collection("/db", $qf:test-coll-name)
    return (
        xmldb:store($confCol, "collection.xconf", $qf:XCONF1),
        xmldb:store($testCol, "test1.xml", $qf:XML),
        xmldb:store($testCol, "test2.xml", $qf:XML)
    )
};

declare
%test:tearDown
function qf:tearDown() {
    xmldb:remove("/db/" || $qf:test-coll-name),
    xmldb:remove($qf:conf-coll-path)
};


(: assert that ft:query-field is only called once for the context sequence, not for each item :)
declare
%test:stats
%test:assertXPath("$result/stats:index[@type eq 'lucene' and @calls eq '1']")
function qf:query-field-context() {
    count(collection("/db/" || $qf:test-coll-name)/*[ft:query-field("testField", "Rüsselsheim", <options/>)])
};
