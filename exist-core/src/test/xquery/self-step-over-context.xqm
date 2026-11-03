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
 : A self step over a node set gives the same nodes whether it is evaluated per context item or, when all its
 : predicates select from the context set as a whole, once over the whole context: a node-set predicate filters, a
 : positional predicate applies to each context item on its own.
 :)
module namespace sso = "http://exist-db.org/xquery/test/self-step-over-context";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $sso:COLLECTION := "/db/test-self-step-over-context";

declare
    %test:setUp
function sso:setup() {
    xmldb:create-collection("/db", substring-after($sso:COLLECTION, "/db/")),
    xmldb:store($sso:COLLECTION, "items.xml",
        <items><item n="1" a="x"><b/>a</item><item n="2">b</item><item n="3" a="y">c</item><item n="4" a="z"><b/>d</item></items>)
};

declare
    %test:tearDown
function sso:tearDown() {
    xmldb:remove($sso:COLLECTION)
};

declare %private function sso:items() as element(item)* {
    collection($sso:COLLECTION)//item
};

declare %private function sso:n($nodes as element(item)*) as xs:string {
    string-join($nodes ! @n, ",")
};

declare
    %test:assertEquals("1,3,4")
function sso:node-set-predicate() {
    let $c := sso:items()
    return sso:n($c/self::item[@a])
};

declare
    %test:assertEquals("1,4")
function sso:node-set-predicates() {
    let $c := sso:items()
    return sso:n($c/self::item[@a][b])
};

declare
    %test:assertEquals("1,2,3,4")
function sso:positional-predicate-per-item() {
    let $c := sso:items()
    return sso:n($c/self::item[1])
};

declare
    %test:assertEquals("1,2,3,4")
function sso:last-per-item() {
    let $c := sso:items()
    return sso:n($c/self::item[last()])
};

declare
    %test:assertEquals("2")
function sso:boolean-predicate() {
    let $c := sso:items()
    return sso:n($c/self::item[. = 'b'])
};

declare
    %test:assertEquals("1,3,4")
function sso:node-set-then-positional() {
    let $c := sso:items()
    return sso:n($c/self::item[@a][1])
};
