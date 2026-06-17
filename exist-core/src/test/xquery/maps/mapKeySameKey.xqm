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
 : Tests for map key comparison via op:same-key (XQuery 3.1 section 17.1).
 :
 : Map keys are compared with op:same-key, which is a TYPE-GROUP comparison:
 :   - the numeric family (xs:decimal/xs:integer/xs:float/xs:double) interchanges,
 :   - the string family (xs:string/xs:anyURI/xs:untypedAtomic) interchanges,
 :   - every other type matches only itself.
 : There is NO key atomization or casting across groups.
 :
 : These tests deliberately use the map{...} CONSTRUCTOR form (which builds a
 : homogeneously-typed map). The qt3/qt4 suites place their cross-family
 : distinctness tests on map:entry() (a mixed-key map), so the constructor path
 : was historically untested - and eXist used to conflate cross-family keys that
 : shared a lexical value (e.g. map{"12":"x"}(12) wrongly returned "x").
 :)
module namespace skt="http://exist-db.org/xquery/test/maps/samekey";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

(: ----------------------------------------------------------------------- :)
(: Cross-family distinctness: keys of different op:same-key families that   :)
(: share a lexical value must NOT match.                                    :)
(: ----------------------------------------------------------------------- :)

declare
    %test:name("string key vs integer lookup are distinct (contains)")
    %test:assertFalse
function skt:string-key-vs-integer-contains() {
    map:contains(map { "12": "x" }, 12)
};

declare
    %test:name("string key vs integer lookup are distinct (get)")
    %test:assertEmpty
function skt:string-key-vs-integer-get() {
    map:get(map { "12": "x" }, 12)
};

declare
    %test:name("string key vs double lookup are distinct (contains)")
    %test:assertFalse
function skt:string-key-vs-double-contains() {
    map:contains(map { "5": 1 }, 5.0e0)
};

declare
    %test:name("string key vs xs:date lookup are distinct (contains)")
    %test:assertFalse
function skt:string-key-vs-date-contains() {
    map:contains(map { "2020-01-01": 1 }, xs:date("2020-01-01"))
};

declare
    %test:name("xs:date key vs string lookup are distinct (contains)")
    %test:assertFalse
function skt:date-key-vs-string-contains() {
    map:contains(map { xs:date("2020-01-01"): 1 }, "2020-01-01")
};

declare
    %test:name("boolean key vs string lookup are distinct (contains)")
    %test:assertFalse
function skt:boolean-key-vs-string-contains() {
    map:contains(map { true(): "x" }, "true")
};

declare
    %test:name("integer key vs string lookup are distinct (contains)")
    %test:assertFalse
function skt:integer-key-vs-string-contains() {
    map:contains(map { 12: "x" }, "12")
};

(: ----------------------------------------------------------------------- :)
(: Numeric family is by VALUE, not by lossy cast: a non-integral lookup     :)
(: must not match an integer key (no truncation).                           :)
(: ----------------------------------------------------------------------- :)

declare
    %test:name("integer key does not match a non-integral double lookup (contains)")
    %test:assertFalse
function skt:integer-key-vs-fractional-double-contains() {
    map:contains(map { 1: "x" }, 1.5e0)
};

declare
    %test:name("integer key does not match a non-integral double lookup (get)")
    %test:assertEmpty
function skt:integer-key-vs-fractional-double-get() {
    map:get(map { 1: "x" }, 1.5e0)
};

(: ----------------------------------------------------------------------- :)
(: Within-family positives: members of the SAME op:same-key family that     :)
(: are value-equal MUST interchange (these must not regress).               :)
(: ----------------------------------------------------------------------- :)

declare
    %test:name("numeric family: integer key matches equal double lookup (contains)")
    %test:assertTrue
function skt:numeric-integer-key-vs-double-contains() {
    map:contains(map { 5: 1 }, 5.0e0)
};

declare
    %test:name("numeric family: integer key matches equal double lookup (get)")
    %test:assertEquals(1)
function skt:numeric-integer-key-vs-double-get() {
    map:get(map { 5: 1 }, 5.0e0)
};

declare
    %test:name("numeric family: decimal key matches equal integer lookup (contains)")
    %test:assertTrue
function skt:numeric-decimal-key-vs-integer-contains() {
    map:contains(map { 5.0: 1 }, 5)
};

declare
    %test:name("string family: string key matches equal xs:anyURI lookup (contains)")
    %test:assertTrue
function skt:string-key-vs-anyuri-contains() {
    map:contains(map { "urn:x": 1 }, xs:anyURI("urn:x"))
};

declare
    %test:name("string family: string key matches equal xs:untypedAtomic lookup (get)")
    %test:assertEquals("x")
function skt:string-key-vs-untypedatomic-get() {
    map:get(map { "12": "x" }, xs:untypedAtomic("12"))
};

declare
    %test:name("string family: anyURI key matches equal string lookup (contains)")
    %test:assertTrue
function skt:anyuri-key-vs-string-contains() {
    map:contains(map { xs:anyURI("urn:x"): 1 }, "urn:x")
};

(: ----------------------------------------------------------------------- :)
(: Same-type self-matches still work after the fix.                         :)
(: ----------------------------------------------------------------------- :)

declare
    %test:name("string key matches equal string lookup (get)")
    %test:assertEquals("x")
function skt:string-key-self-get() {
    map:get(map { "12": "x" }, "12")
};

declare
    %test:name("xs:date key matches equal xs:date lookup (contains)")
    %test:assertTrue
function skt:date-key-self-contains() {
    map:contains(map { xs:date("2020-01-01"): 1 }, xs:date("2020-01-01"))
};
