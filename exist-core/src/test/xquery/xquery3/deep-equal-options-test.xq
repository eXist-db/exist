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

module namespace det = "http://exist-db.org/test/deep-equal-options";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: === Whitespace options === :)

declare
    %test:assertTrue
function det:whitespace-strip-basic() {
    deep-equal(parse-xml('<a> <b/> </a>'), parse-xml('<a><b/></a>'), map { 'whitespace': 'strip' })
};

declare
    %test:assertFalse
function det:whitespace-strip-attr() {
    (: attribute values should NOT be affected by whitespace:strip :)
    deep-equal(parse-xml('<a> <b x=" "/> </a>'), parse-xml('<a><b x=""/></a>'), map { 'whitespace': 'strip' })
};

declare
    %test:assertTrue
function det:whitespace-normalize-basic() {
    deep-equal('bedtime', ' bedtime ', map { 'whitespace': 'normalize' })
};

declare
    %test:assertTrue
function det:whitespace-normalize-attr() {
    (: Attribute values are normalized, whitespace-only text nodes normalize to empty :)
    deep-equal(
        parse-xml('<a> <b c="x x x"/> </a>'),
        parse-xml('<a><b c=" x  x  x "/></a>'),
        map { 'whitespace': 'normalize' })
};

declare
    %test:assertFalse
function det:whitespace-normalize-attr-different() {
    (: Attribute values differ after normalization :)
    deep-equal(
        parse-xml('<a> <b c="x x x"/> </a>'),
        parse-xml('<a><b c="xxx"/></a>'),
        map { 'whitespace': 'normalize' })
};

(: === Options validation === :)

declare
    %test:assertError("XPTY0004")
function det:options-invalid-key() {
    deep-equal(1, 2, map { 'bifurcation': true() })
};

declare
    %test:assertTrue
function det:options-valid-booleans() {
    deep-equal((), (), map {
        'base-uri': true(), 'comments': true(), 'debug': true(),
        'id-property': true(), 'idrefs-property': true(),
        'in-scope-namespaces': true(), 'namespace-prefixes': true(), 'nilled-property': true(),
        'processing-instructions': true(), 'timezones': true(), 'type-annotations': true(),
        'type-variety': true(), 'typed-values': true()
    })
};

(: === Ordered/unordered === :)

declare
    %test:assertTrue
function det:unordered-basic() {
    deep-equal((1, 2), (2, 1), map { 'ordered': false() })
};

declare
    %test:assertFalse
function det:ordered-basic() {
    deep-equal((1, 2), (2, 1), map { 'ordered': true() })
};

(: === Comments option === :)

declare
    %test:assertTrue
function det:comments-default-ignored() {
    (: By default comments are ignored :)
    deep-equal(
        parse-xml('<a><!--foo--></a>'),
        parse-xml('<a><!--bar--></a>'))
};

declare
    %test:assertFalse
function det:comments-true-compared() {
    (: With comments:true, different comments matter :)
    deep-equal(
        parse-xml('<a><!--foo--></a>'),
        parse-xml('<a><!--bar--></a>'),
        map { 'comments': true() })
};

(: === Processing instructions === :)

declare
    %test:assertTrue
function det:pi-default-ignored() {
    deep-equal(
        parse-xml('<a><?pi1 data1?></a>'),
        parse-xml('<a><?pi2 data2?></a>'))
};

declare
    %test:assertFalse
function det:pi-true-compared() {
    deep-equal(
        parse-xml('<a><?pi1 data1?></a>'),
        parse-xml('<a><?pi2 data2?></a>'),
        map { 'processing-instructions': true() })
};

(: === Function item comparison === :)

declare
    %test:assertFalse
function det:function-items-different() {
    deep-equal(true#0, false#0)
};

declare
    %test:assertTrue
function det:function-items-same() {
    let $f := true#0
    return deep-equal($f, $f)
};

declare
    %test:assertTrue
function det:function-items-same-name() {
    (: Two separate references to same named function should be equal :)
    deep-equal(true#0, true#0)
};

declare function local:helper() { 42 };

declare
    %test:assertTrue
function det:function-items-user-defined-same() {
    deep-equal(local:helper#0, local:helper#0)
};
