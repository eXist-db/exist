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
 : The pattern and the flags of fn:matches may come from the context node: matches(@n, @pat) compares each item with its own pattern.
 : On stored nodes the predicate used to run for the whole step at once, so the pattern was evaluated for all the items and the call
 : raised XPTY0004 ("exactly one, got 2"). See https://github.com/eXist-db/exist/issues/7056
 :
 : Each test evaluates a path on a stored document and on an in-memory copy of it, with the optimizer on and off, and returns the sorted
 : n of the items it selects, one answer for each of the four ways, so that a wrong answer or an error shows which way it came from.
 : The answers are the ones XPath gives.
 :)
module namespace mp="http://exist-db.org/xquery/test/matches-pattern-from-the-node";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $mp:COLLECTION_NAME := "test-matches-pattern-from-the-node";
declare variable $mp:COLLECTION := "/db/" || $mp:COLLECTION_NAME;

declare
    %test:setUp
function mp:setup() {
    xmldb:create-collection("/db", $mp:COLLECTION_NAME),
    (: the pattern of each item is its own n :)
    xmldb:store($mp:COLLECTION, "attribute.xml", <r><item n="a" pat="a"/><item n="b" pat="b"/></r>),
    (: the same in a child element :)
    xmldb:store($mp:COLLECTION, "child.xml", <r><item n="a"><p>a</p></item><item n="b"><p>b</p></item></r>),
    (: only the first item matches its pattern :)
    xmldb:store($mp:COLLECTION, "some.xml", <r><item n="a" pat="a"/><item n="b" pat="x"/></r>),
    (: the flags come from the item: only the first ignores the case :)
    xmldb:store($mp:COLLECTION, "flags.xml", <r><item n="a" fl="i"/><item n="b" fl=""/></r>)
};

declare
    %test:tearDown
function mp:tear-down() {
    xmldb:remove($mp:COLLECTION)
};

(:~ The sorted n of the items the path selects. ROOT stands for the document or for an in-memory copy of it. :)
declare %private function mp:run($file as xs:string, $path as xs:string, $stored as xs:boolean, $optimize as xs:boolean) as xs:string {
    let $doc := "doc('" || $mp:COLLECTION || "/" || $file || "')"
    let $root := if ($stored) then $doc else "(" || $doc || " ! document { ./node() })"
    let $prolog := if ($optimize) then "" else "declare option exist:optimize 'enable=no'; "
    return util:eval($prolog || "string-join(sort((" || replace($path, "ROOT", $root) || ")/@n/string()), ' ')")
};

(:~ stored and optimized, stored and as written, in memory and optimized, in memory and as written :)
declare %private function mp:all($file as xs:string, $path as xs:string) as xs:string {
    string-join(
        for $stored in (true(), false())
        for $optimize in (true(), false())
        return mp:run($file, $path, $stored, $optimize),
        "|")
};

declare
    %test:assertEquals("a b|a b|a b|a b")
function mp:pattern-in-an-attribute() {
    mp:all("attribute.xml", "ROOT//item[matches(@n, @pat)]")
};

declare
    %test:assertEquals("a b|a b|a b|a b")
function mp:pattern-in-a-child() {
    mp:all("child.xml", "ROOT//item[matches(@n, p)]")
};

declare
    %test:assertEquals("a b|a b|a b|a b")
function mp:pattern-is-the-first-of-a-path() {
    mp:all("attribute.xml", "ROOT//item[matches(@n, (@pat)[1])]")
};

declare
    %test:assertEquals("a b|a b|a b|a b")
function mp:pattern-in-a-function() {
    mp:all("attribute.xml", "ROOT//item[matches(@n, string(@pat))]")
};

declare
    %test:assertEquals("a b|a b|a b|a b")
function mp:pattern-built-from-the-item() {
    mp:all("attribute.xml", "ROOT//item[matches(@n, concat('^', @pat))]")
};

(: the pattern is the item's own: one of the two items matches :)
declare
    %test:assertEquals("a|a|a|a")
function mp:only-one-item-matches() {
    mp:all("some.xml", "ROOT//item[matches(@n, @pat)]")
};

declare
    %test:assertEquals("a|a|a|a")
function mp:flags-come-from-the-item() {
    mp:all("flags.xml", "ROOT//item[matches(@n, 'A', @fl)]")
};

(: a pattern that does not read the focus is unchanged :)
declare
    %test:assertEquals("a|a|a|a")
function mp:literal-pattern() {
    mp:all("attribute.xml", "ROOT//item[matches(@n, 'a')]")
};
