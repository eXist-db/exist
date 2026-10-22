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
 : A query over collections configured differently counts the facets of every configuration: a dimension one of
 : them defines is counted for all the hits, and a dimension two of them define differently is an error.
 :)
module namespace mfc = "http://exist-db.org/xquery/lucene/test/mixed-facet-configs";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $mfc:PARENT := "test-mixed-facet-configs";
declare variable $mfc:CONFLICT := "test-mixed-facet-configs-conflict";

declare %private function mfc:xconf($facets as element()*) as element() {
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="p">{ $facets }</text>
            </lucene>
        </index>
    </collection>
};

declare %private function mfc:facet($dimension as xs:string, $expression as xs:string, $hierarchical as xs:boolean)
        as element() {
    <facet xmlns="http://exist-db.org/collection-config/1.0" dimension="{ $dimension }" expression="{ $expression }">{
        if ($hierarchical) then attribute hierarchical { "yes" } else ()
    }</facet>
};

declare %private function mfc:store($parent as xs:string, $sub as xs:string, $facets as element()*) {
    let $config := xmldb:create-collection("/db/system/config/db", $parent)
    let $col := xmldb:create-collection("/db", $parent)
    let $_ := xmldb:store(xmldb:create-collection($config, $sub), "collection.xconf", mfc:xconf($facets))
    return xmldb:store(xmldb:create-collection($col, $sub), "doc.xml",
        <div><p lang="en" year="2024">a word</p><p lang="de" year="2025">a word</p></div>)
};

declare
    %test:setUp
function mfc:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    return (
        (: "lang" in a, "year" in b, "kind" flat in both :)
        mfc:store($mfc:PARENT, "a", (mfc:facet("kind", "'para'", false()), mfc:facet("lang", "@lang", false()))),
        mfc:store($mfc:PARENT, "b", (mfc:facet("kind", "'para'", false()), mfc:facet("year", "@year", false()))),
        (: "kind" flat in one, hierarchical in the other :)
        mfc:store($mfc:CONFLICT, "flat", mfc:facet("kind", "'para'", false())),
        mfc:store($mfc:CONFLICT, "hierarchical", mfc:facet("kind", "('para', 'note')", true()))
    )
};

declare
    %test:tearDown
function mfc:tearDown() {
    for $parent in ($mfc:PARENT, $mfc:CONFLICT)
    return (xmldb:remove("/db/" || $parent), xmldb:remove("/db/system/config/db/" || $parent))
};

(: each configuration's own dimensions are counted, for the hits over both :)
declare
    %test:assertEquals(4, 1, 1, 1, 1, 4)
function mfc:facets-of-every-configuration() {
    let $hits := collection("/db/" || $mfc:PARENT)//p[ft:query(., "word")]
    let $lang := ft:facets($hits, "lang", ())
    let $year := ft:facets($hits, "year", ())
    return (count($hits), $lang?en, $lang?de, $year("2024"), $year("2025"), ft:facets($hits, "kind", ())?para)
};

declare
    %test:assertError("EXXQDYFT0005")
function mfc:a-dimension-configured-differently-is-an-error() {
    count(collection("/db/" || $mfc:CONFLICT)//p[ft:query(., "word")])
};

