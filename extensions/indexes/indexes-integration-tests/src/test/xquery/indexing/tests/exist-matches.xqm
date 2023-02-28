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

module namespace em="http://exist-db.org/xquery/indexing/tests/exist-matches";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $em:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="x"/>
            </lucene>
            <ngram qname="y"/>
        </index>
    </collection>;

declare variable $em:testCol := xmldb:create-collection("/db", "exist-matches");
declare variable $em:confCol := xmldb:create-collection("/db/system/config/db", "exist-matches");

declare
    %test:setUp
function em:setup() {

    (
        xmldb:store($em:confCol, "collection.xconf", $em:XCONF1),
        xmldb:store($em:testCol, "test1.xml",
                <root>
                        <x>Hello</x>
                        <y>Hello</y>
                </root>
        )
    )
};

declare
    %test:tearDown
function em:tearDown() {
    xmldb:remove($em:testCol),
    xmldb:remove($em:confCol)
};

declare
    %test:args("Hello")
    (:we are only seeing one match for exist match in this state because :)
    (:the matching is broken in both ft:query and ngram check  https://github.com/eXist-db/exist/issues/2102#issuecomment-1442410050 for more info:)
    %test:assertEquals(1) (:this should be 2 instead of 1:)
function em:expand-node-lucene-ngram-matches($query as xs:string) {
    let $doc := doc($em:testCol || "/test1.xml")
    let $x-hits := $doc/root[ft:query(x, $query)]
    let $y-hits := $doc/root[ngram:contains(y, $query)]
    let $hits := ($x-hits | $y-hits)
    return count(util:expand($hits)//exist:match)
};
