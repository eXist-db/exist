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

module namespace fnck="http://exist-db.org/xquery/test/function_collation_key";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function fnck:default-equal() {
    let $first := fn:collation-key("a")
    let $second := fn:collation-key("a")
    return $first eq $second
};

declare
    %test:assertTrue
function fnck:default-not-equal() {
    let $first := fn:collation-key("a")
    let $second := fn:collation-key("b")
    return $first ne $second
};

declare
    %test:assertTrue
function fnck:default-not-equal-ignore-case() {
    let $first := fn:collation-key("a")
    let $second := fn:collation-key("A")
    return $first ne $second
};

declare
    %test:assertTrue
function fnck:exist-equal() {
    let $first := fn:collation-key("a", "http://exist-db.org/collation")
    let $second := fn:collation-key("a", "http://exist-db.org/collation")
    return $first eq $second
};

declare
    %test:assertTrue
function fnck:exist-not-equal() {
    let $first := fn:collation-key("a", "http://exist-db.org/collation")
    let $second := fn:collation-key("b", "http://exist-db.org/collation")
    return $first ne $second
};

declare
    %test:assertTrue
function fnck:exist-not-equal-ignore-case() {
    let $first := fn:collation-key("a", "http://exist-db.org/collation")
    let $second := fn:collation-key("A", "http://exist-db.org/collation")
    return $first ne $second
};

declare
    %test:assertError("FOCH0002")
function fnck:invalid-uri() {
    fn:collation-key("a", "")
};

declare
    %test:assertTrue
function fnck:uca-equal() {
    let $first := fn:collation-key("a", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")
    let $second := fn:collation-key("a", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")
    return $first eq $second
};

declare
    %test:assertTrue
function fnck:uca-equal-ignore-case() {
    let $first := fn:collation-key("a", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")
    let $second := fn:collation-key("A", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")
    return $first eq $second
};

declare
    %test:assertTrue
function fnck:uca-not-equal() {
    let $first := fn:collation-key(fn:codepoints-to-string((37, 65500, 37)))
    let $second := fn:collation-key(fn:codepoints-to-string((37, 100000, 37)))
    return $first lt $second
};
