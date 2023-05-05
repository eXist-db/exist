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

(:~
 : Test optimization of positional predicates combined with the following
 : and preceding axes.
 :)
module namespace ot="http://exist-db.org/xquery/optimizer/test/positional";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ot:COLLECTION_NAME := "optimizertest";
declare variable $ot:COLLECTION := "/db/" || $ot:COLLECTION_NAME;
declare variable $ot:DOC := $ot:COLLECTION || "/test.xml";
declare variable $ot:DOC_NESTED := $ot:COLLECTION || "/nested.xml";
declare variable $ot:DOC_NESTED2 := $ot:COLLECTION || "/nested2.xml";
declare variable $ot:DOC_SIBLINGS := $ot:COLLECTION || "/siblings.xml";

declare variable $ot:DATA :=
    <words>{ot:generate-words(50000)}</words>;

declare variable $ot:NESTED :=
    <test>
        <div>{ot:generate-words(20)}</div>
        <div>{ot:generate-words(20)}</div>
    </test>;

declare variable $ot:NESTED2 :=
    <test>
        <line>
            <word>XPath</word>
            <word>developers</word>
            <word>enjoy</word>
        </line>
        <line>
            <word>working</word>
            <word>with</word>
        </line>
        <line>
            <word>the</word>
            <word>following</word>
            <word>axis</word>
        </line>
    </test>;

declare variable $ot:SIBLINGS :=
    document {
        <test>
            <a> <s>A</s> <n>1</n> </a>
            <a> <s>Z</s> <n>2</n> </a>
            <a> <s>B</s> <n>3</n> </a>
            <a> <s>Z</s> <n>4</n> </a>
            <a> <s>C</s> <n>5</n> </a>
            <a> <s>Z</s> <n>6</n> </a>
        </test>
    };

declare function ot:generate-words($count as xs:integer) {
    for $i in (1 to $count) return <w xml:id="{$i}">{$i}</w>
};

declare
    %test:setUp
function ot:setup() {
    xmldb:create-collection("/db", $ot:COLLECTION_NAME),
    xmldb:store($ot:COLLECTION, "test.xml", $ot:DATA),
    xmldb:store($ot:COLLECTION, "nested.xml", $ot:NESTED),
    xmldb:store($ot:COLLECTION, "nested2.xml", $ot:NESTED2),
    xmldb:store($ot:COLLECTION, "siblings.xml", $ot:SIBLINGS)
};

declare
    %test:tearDown
function ot:cleanup() {
    xmldb:remove($ot:COLLECTION)
};

declare
    %test:assertEquals("<w xml:id='25001'>25001</w>")
function ot:simple-following() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1]
};

declare
    %test:assertEquals("<w xml:id='25001'>25001</w>")
function ot:simple-following-node() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::node()[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-node() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::node()[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-in-for() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i in 1 to 3
    return
        $w/following::node()[$i]
};

declare
    %test:assertEquals("25001")
function ot:simple-following-text() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::text()[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-text() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::text()[1]
};

declare
    %test:assertEquals("<w xml:id='1'>1</w>")
function ot:simple-following-nested() {
    let $w := doc($ot:DOC_NESTED)/test/*[1]/w[@xml:id='20']
    return
        $w/following::*[2]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-nested() {
    let $w := doc($ot:DOC_NESTED)/test/*[1]/w[@xml:id='20']
    return
        $w/following::*[2]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-simple-preceding() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/preceding::*[1]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-simple-preceding-sibling() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/preceding-sibling::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-sibling() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following-sibling::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-multiple-filters() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-operator() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i in 1 to 5
    return
        $w/following::*[$i + 1][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-operator2() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i in 1 to 5
    return
        $w/following::*[1 + $i][self::w]
};

declare
    %test:assertEquals("<word>enjoy</word>", "<word>developers</word>")
function ot:optimize-following-filter-with-at() {
    let $word := doc($ot:DOC_NESTED2)//line/word[. = 'working']
    for $i at $p in (1 to 2)
    return
        $word/preceding::word[$p]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:no-optimize-following-filter-with-function() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    let $i := 1
    return
        (: not optimized because max may return any atomic value, not just numbers :)
        $w/following::*[max(($i, 2))][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-function() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[count(('a', 'b', 'c'))][self::w]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-following-filter-position() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[position() = 1]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-following-filter-last() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[last()]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-following-nested-filter() {
    doc($ot:DOC)//w[@xml:id='25000'][following::*[1] = "25001"]
};

declare
    %test:assertEquals("<a><s>Z</s><n>4</n></a>")
function ot:following-sibling-nested-filter() {
    doc($ot:DOC_SIBLINGS)//a[following-sibling::*[1]/s = 'C']
};

declare
    %test:assertEquals("<a><s>Z</s><n>4</n></a>")
function ot:preceding-sibling-nested-filter() {
    doc($ot:DOC_SIBLINGS)//a[preceding-sibling::*[1]/s = 'B']
};
