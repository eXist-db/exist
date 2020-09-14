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
    These tests emerged from research regarding
    https://github.com/eXist-db/exist/issues/291

    The xquery-parser MUST recognize a curly brace expression,
    if it starts with a literal string.
~:)
module namespace litcurl="http://exist-db.org/xquery/test/literals-in-curlies";
declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:pending
    %test:assertEquals("<root a='a'/>")
function litcurl:literalString() {
(:
 : Since the line below raises a static error, it cannot be tested yet.
 : When the xquery parser is fixed, uncomment it and remove the zero.
 : <root a="{'a'}"/> 
 :)
    0
};

(:~ other literals ~:)
declare
    %test:assertEquals("<root a='true'/>")
function litcurl:literalBooleanTrue() {
    <root a="{true()}"/>
};

declare
    %test:assertEquals("<root a='false'/>")
function litcurl:literalBooleanFalse() {
    <root a="{false()}"/>
};

declare
    %test:assertEquals("<root a='0'/>")
function litcurl:literalNumberZero() {
    <root a="{0}"/>
};

declare
    %test:assertEquals("<root a='1'/>")
function litcurl:literalNumberOne() {
    <root a="{1}"/>
};
declare
    %test:assertEquals("<root a='-1'/>")
function litcurl:literalNumberNegativeOne() {
    <root a="{-1}"/>
};

declare
    %test:assertEquals("<root a='1.1111'/>")
function litcurl:literalDecimal() {
    <root a="{1.1111}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:typeConversionToString() {
    <root a="{xs:string('a')}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:concatStringWithEmptySequence() {
    <root a="{() || 'a'}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:sequenceWithOneLiteralString() {
    <root a="{('a')}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:firstArrayElement() {
    <root a="{['a']?1}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:variableSubstitution() {
    let $a := 'a'
    return <root a="{$a}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:stringConstructor() {
    <root a="{``[a]``}"/>
};

declare
    %test:assertEquals("<root a='a'/>")
function litcurl:mapProperty() {
    <root a="{map{'a':'a'}?a}"/>
};
