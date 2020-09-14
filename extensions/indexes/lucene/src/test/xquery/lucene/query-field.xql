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

declare variable $qf:testCol := xmldb:create-collection("/db", "queryfieldtest");
declare variable $qf:confCol := xmldb:create-collection("/db/system/config/db", "queryfieldtest");

declare
%test:setUp
function qf:setup() {

    (
        xmldb:store($qf:confCol, "collection.xconf", $qf:XCONF1),
        xmldb:store($qf:testCol, "test1.xml",
                <test>
                    <p>Rüsselsheim</p>
                    <p>Russelsheim</p>
                    <p>Māori</p>
                    <p>Maori</p>
                </test>
        ),
        xmldb:store($qf:testCol, "test2.xml",
                <test>
                    <p>Rüsselsheim</p>
                    <p>Russelsheim</p>
                    <p>Māori</p>
                    <p>Maori</p>
                </test>
        )
    )
};

declare
%test:tearDown
function qf:tearDown() {
    xmldb:remove($qf:testCol),
    xmldb:remove($qf:confCol)
};


(: assert that ft:query-field is only called once for the context sequence, not for each item :)
declare
%test:stats
%test:assertXPath("$result/stats:index[@type eq 'lucene' and @calls eq '1']")
function qf:query-field-context() {
    count(collection($qf:testCol)/*[ft:query-field("testField", "Rüsselsheim", <options/>)])
};
