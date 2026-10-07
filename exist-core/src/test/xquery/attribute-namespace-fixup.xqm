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
 : When a constructed element copies an attribute whose prefix the element binds to another
 : namespace, the attribute's prefix is resolved from the element's own bindings. Each test
 : serializes the constructed element and parses it back, so it checks the namespace
 : declarations that are actually written, not only the attribute's name in memory.
 :)
module namespace anf="http://exist-db.org/xquery/test/attribute-namespace-fixup";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~ the copied element, serialized and parsed back :)
declare %private function anf:reparsed($element as element()) as element() {
    parse-xml(serialize($element))/*
};

declare
    %test:assertEquals("http://www.example.com/parent1")
function anf:rebinds-a-prefix-the-element-binds-elsewhere() {
    let $x := <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
    let $new := anf:reparsed(<new xmlns:foo="http://www.example.com">{$x//@*:attr1}</new>)
    return namespace-uri($new/@*:attr1)
};

(:~ XQTS Constr-inscope-4: two attributes with the same prefix and different URIs. :)
declare
    %test:assertEquals("http://www.example.com/parent1", "http://www.example.com/parent2")
function anf:separates-two-attributes-that-share-a-prefix() {
    let $x :=
        <inscope>
            <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
            <parent2 xmlns:foo="http://www.example.com/parent2" foo:attr2="attr2"/>
        </inscope>
    let $new := anf:reparsed(<new>{$x//@*:attr1, $x//@*:attr2}</new>)
    return (namespace-uri($new/@*:attr1), namespace-uri($new/@*:attr2))
};

(:~ A prefix the element already binds to the attribute's URI is reused rather than inventing one. :)
declare
    %test:assertEquals("http://www.example.com/parent1", "bar")
function anf:reuses-a-prefix-the-element-already-binds-to-the-uri() {
    let $x := <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
    let $new := anf:reparsed(
        <new xmlns:foo="http://www.example.com" xmlns:bar="http://www.example.com/parent1">{$x//@*:attr1}</new>)
    return (namespace-uri($new/@*:attr1), prefix-from-QName(node-name($new/@*:attr1)))
};

(:~ A generated prefix must not be one the element already uses for another URI. :)
declare
    %test:assertEquals("http://www.example.com/parent1", "XXX1")
function anf:generates-a-prefix-the-element-does-not-use() {
    let $x := <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
    let $new := anf:reparsed(
        <new xmlns:foo="http://www.example.com" xmlns:XXX="urn:taken">{$x//@*:attr1}</new>)
    return (namespace-uri($new/@*:attr1), prefix-from-QName(node-name($new/@*:attr1)))
};

(:~ No conflict: the attribute keeps its prefix. :)
declare
    %test:assertEquals("foo")
function anf:keeps-a-prefix-that-does-not-conflict() {
    let $x := <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
    let $new := anf:reparsed(<new>{$x//@*:attr1}</new>)
    return prefix-from-QName(node-name($new/@*:attr1))
};
