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
 : last() and position() in the key of a comparison, and in an operand of "and", are those of the step the predicate belongs to.
 : See https://github.com/eXist-db/exist/issues/7068
 :
 : Every wrap has two item children (n="i" with a="hit", n="i+10" with a="miss") and an other element, so for the items of a wrap
 : last() is 2 and position() is 1 or 2, and for all the items of the collection last() is 20. Each test evaluates a path on the
 : stored documents and on in-memory copies, with the optimizer on and off, and returns the sorted n of the nodes it selects,
 : one answer for each of the four ways, so that a wrong answer shows which way it came from. The answers are the ones XPath gives.
 :)
module namespace fk="http://exist-db.org/xquery/test/focus-in-key";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fk:COLLECTION_NAME := "test-focus-in-key";
declare variable $fk:COLLECTION := "/db/" || $fk:COLLECTION_NAME;

declare
    %test:setUp
function fk:setup() {
    xmldb:create-collection("/db", $fk:COLLECTION_NAME),
    for $i in 1 to 10
    return xmldb:store($fk:COLLECTION, "d" || $i || ".xml",
        <wrap><item n="{$i}" a="hit"/><item n="{$i + 10}" a="miss"/><other n="o{$i}"/></wrap>)
};

declare
    %test:tearDown
function fk:tear-down() {
    xmldb:remove($fk:COLLECTION)
};

(:~ The sorted n of the nodes the path selects. ROOT stands for the collection or for in-memory copies of its documents. :)
declare %private function fk:run($path as xs:string, $stored as xs:boolean, $optimize as xs:boolean) as xs:string {
    let $root := if ($stored) then "collection('" || $fk:COLLECTION || "')" else "(collection('" || $fk:COLLECTION || "') ! document { ./node() })"
    let $prolog := if ($optimize) then "" else "declare option exist:optimize 'enable=no'; "
    return util:eval($prolog || "string-join(sort((" || replace($path, "ROOT", $root) || ")/@n/string()), ' ')")
};

(:~ stored and optimized, stored and as written, in memory and optimized, in memory and as written :)
declare %private function fk:all($path as xs:string) as xs:string {
    string-join(
        for $stored in (true(), false())
        for $optimize in (true(), false())
        return fk:run($path, $stored, $optimize),
        "|")
};

(: The key of a comparison calls last() inside an if: last() is the number of items of the step for each wrap, 2, so the key is 'hit' :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9")
function fk:key-last-in-if() {
    fk:all("ROOT/wrap/item[@a = (if (last() = 2) then 'hit' else 'miss')]")
};

(: last() inside string() :)
declare
    %test:assertEquals("2|2|2|2")
function fk:key-last-in-function() {
    fk:all("ROOT/wrap/item[@n = string(last())]")
};

(: last() inside concat() :)
declare
    %test:assertEquals("2|2|2|2")
function fk:key-last-in-concat() {
    fk:all("ROOT/wrap/item[@n = concat('', last())]")
};

(: last() bound by a let in the key :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9")
function fk:key-last-in-let() {
    fk:all("ROOT/wrap/item[@a = (let $l := last() return if ($l = 2) then 'hit' else 'miss')]")
};

(: the same key with a value comparison :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9")
function fk:key-last-in-value-comparison() {
    fk:all("ROOT/wrap/item[@a eq (if (last() = 2) then 'hit' else 'miss')]")
};

(: contains() is rewritten into a comparison :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9")
function fk:key-last-in-contains() {
    fk:all("ROOT/wrap/item[contains(@a, (if (last() = 2) then 'hit' else 'miss'))]")
};

(: position() inside an if: 1 for the first item of a wrap, 2 for the second :)
declare
    %test:assertEquals("1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9|1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9|1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9|1 10 11 12 13 14 15 16 17 18 19 2 20 3 4 5 6 7 8 9")
function fk:key-position-in-if() {
    fk:all("ROOT/wrap/item[@a = (if (position() = 1) then 'hit' else 'miss')]")
};

(: position() inside string() :)
declare
    %test:assertEquals("1|1|1|1")
function fk:key-position-in-function() {
    fk:all("ROOT/wrap/item[@n = string(position())]")
};

(: the filtered sequence has 20 items, so last() is 20 :)
declare
    %test:assertEquals("20|20|20|20")
function fk:key-last-over-a-variable() {
    fk:all("(let $i := ROOT/wrap/item return $i[@n = string(last())])")
};

(: the union of the item and other children of a wrap has 3 nodes :)
declare
    %test:assertEquals("3|3|3|3")
function fk:key-last-after-union() {
    fk:all("ROOT/wrap/(item | other)[@n = string(last())]")
};

(: control: a literal key :)
declare
    %test:assertEquals("1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9|1 10 2 3 4 5 6 7 8 9")
function fk:literal-key() {
    fk:all("ROOT/wrap/item[@a = 'hit']")
};

(: control: and without a focus :)
declare
    %test:assertEquals("2|2|2|2")
function fk:and-without-focus() {
    fk:all("ROOT/wrap/item[@a = 'hit' and @n = '2']")
};

(: control: a key that depends on the item, not on the position :)
declare
    %test:assertEquals("1 11 12 13 14 15 16 17 18 19 20|1 11 12 13 14 15 16 17 18 19 20|1 11 12 13 14 15 16 17 18 19 20|1 11 12 13 14 15 16 17 18 19 20")
function fk:key-reads-the-item() {
    fk:all("ROOT/wrap/item[@a = (if (@n = '1') then 'hit' else 'miss')]")
};

(: control: last() as the left operand :)
declare
    %test:assertEquals("2|2|2|2")
function fk:left-operand-asks-last() {
    fk:all("ROOT/wrap/item[xs:integer(@n) = last()]")
};
