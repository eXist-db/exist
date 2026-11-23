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
 : A key that reads the focus, the context item, its position or the size of the context, is evaluated for each item of the step with
 : that item's focus. A lookup in the index evaluates its key once for all the items, so the range rewriter leaves such a comparison as
 : written. A key that does not read the focus, a literal, a variable or a function of them, is still looked up.
 : See https://github.com/eXist-db/exist/issues/7068 and https://github.com/eXist-db/exist/issues/6956
 :
 : Every wrap has two item children (n="i" with a="hit", n="i+10" with a="miss"), so for the items of a wrap last() is 2 and position()
 : is 1 or 2. The answers are the ones XPath gives: the sorted n of the nodes selected.
 :)
module namespace krf="http://exist-db.org/xquery/range/key-reads-focus/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";
declare namespace range="http://exist-db.org/xquery/range";

declare variable $krf:COLLECTION_NAME := "test-range-key-reads-focus";
declare variable $krf:COLLECTION := "/db/" || $krf:COLLECTION_NAME;
declare variable $krf:CONF := "/db/system/config/db/" || $krf:COLLECTION_NAME;

declare variable $krf:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <range>
                <create qname="@a" type="xs:string"/>
                <create qname="@n" type="xs:string"/>
                <create qname="item"><field name="krf-a" match="@a" type="xs:string"/></create>
            </range>
        </index>
    </collection>;

declare
    %test:setUp
function krf:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $krf:COLLECTION_NAME),
    xmldb:store($krf:CONF, "collection.xconf", $krf:XCONF),
    xmldb:create-collection("/db", $krf:COLLECTION_NAME),
    for $i in 1 to 10
    return xmldb:store($krf:COLLECTION, "d" || $i || ".xml",
        <wrap><item n="{$i}" a="hit"/><item n="{$i + 10}" a="miss"/></wrap>)
};

declare
    %test:tearDown
function krf:tearDown() {
    xmldb:remove($krf:COLLECTION),
    xmldb:remove($krf:CONF)
};

declare %private function krf:answer($nodes as node()*) as xs:string {
    string-join(sort($nodes/@n/string()), " ")
};

(: last() is 2 for the items of a wrap, so the key is 'hit' :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9")
function krf:last-in-an-if() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@a = (if (last() = 2) then 'hit' else 'miss')])
};

declare
    %test:assertEquals("2")
function krf:last-in-a-function() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@n = string(last())])
};

(: position() is 1 for the first item of a wrap and 2 for the second :)
declare
    %test:assertEquals("1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9")
function krf:position-in-an-if() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@a = (if (position() = 1) then 'hit' else 'miss')])
};

declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9")
function krf:last-in-the-pattern-of-matches() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[matches(@a, (if (last() = 2) then '^hit$' else '^miss$'))])
};

(: a key that depends on the item: 'hit' for the item with n = 1, 'miss' for the others :)
declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20")
function krf:key-depends-on-the-item() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@a = (if (@n = '1') then 'hit' else 'miss')])
};

(: the key is the n of the first item of the wrap :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9")
function krf:key-is-a-path-of-the-wrap() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@n = ../item[1]/@n])
};

declare
    %test:assertEquals("1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9")
function krf:key-is-a-function-of-the-item() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[@a = string(@a)])
};

(: the index functions evaluate their key for each item of the step, whether the optimizer pre-selects or not :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9")
function krf:eq-last-in-an-if() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[range:eq(@a, (if (last() = 2) then 'hit' else 'miss'))])
};

declare
    %test:assertEquals("1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9")
function krf:eq-position-in-an-if() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[range:eq(@a, (if (position() = 1) then 'hit' else 'miss'))])
};

declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20")
function krf:eq-key-depends-on-the-item() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[range:eq(@a, (if (@n = '1') then 'hit' else 'miss'))])
};

declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20")
function krf:eq-key-depends-on-the-item-of-a-variable() {
    let $items := collection($krf:COLLECTION)/wrap/item
    return krf:answer($items[range:eq(@a, (if (@n = '1') then 'hit' else 'miss'))])
};

declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20")
function krf:matches-pattern-depends-on-the-item() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[range:matches(@a, (if (@n = '1') then '^hit$' else '^miss$'))])
};

declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20")
function krf:field-eq-key-depends-on-the-item() {
    krf:answer(collection($krf:COLLECTION)/wrap/item[range:field-eq("krf-a", (if (@n = '1') then 'hit' else 'miss'))])
};

(: the keys that do not read the focus are looked up in the index :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function krf:literal-key-uses-the-index() {
    collection($krf:COLLECTION)/wrap/item[@a = 'hit']
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function krf:variable-key-uses-the-index() {
    let $k := 'hit'
    return collection($krf:COLLECTION)/wrap/item[@a = $k]
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function krf:function-of-a-literal-uses-the-index() {
    collection($krf:COLLECTION)/wrap/item[@a = lower-case('HIT')]
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function krf:function-of-a-variable-uses-the-index() {
    let $k := 'HIT'
    return collection($krf:COLLECTION)/wrap/item[@a = lower-case($k)]
};

(: the keys that read the focus are evaluated as written :)
declare
    %test:stats
    %test:assertXPath("not(exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']))")
function krf:key-depending-on-the-item-is-not-looked-up() {
    collection($krf:COLLECTION)/wrap/item[@a = (if (@n = '1') then 'hit' else 'miss')]
};

declare
    %test:stats
    %test:assertXPath("not(exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']))")
function krf:last-in-the-key-is-not-looked-up() {
    collection($krf:COLLECTION)/wrap/item[@n = string(last())]
};
