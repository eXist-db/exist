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
 : Tests for method="csv" serialization.
 :)
module namespace csv = "http://exist-db.org/xquery/test/csv-serializer";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: === Array-of-arrays === :)

declare
    %test:assertEquals('"a","b"
"c","d"
')
function csv:array-of-arrays() {
    serialize([["a","b"],["c","d"]], map { "method": "csv" })
};

declare
    %test:assertEquals('"1","2","3"
')
function csv:single-row-array() {
    serialize([["1","2","3"]], map { "method": "csv" })
};

(: === Empty input === :)

declare
    %test:assertEquals("")
function csv:empty-sequence() {
    serialize((), map { "method": "csv" })
};

declare
    %test:assertEquals("")
function csv:empty-array() {
    serialize([], map { "method": "csv" })
};

(: === Custom delimiters === :)

declare
    %test:assertEquals('a;b
c;d
')
function csv:custom-field-delimiter() {
    serialize([["a","b"],["c","d"]], map {
        "method": "csv",
        "csv.field-delimiter": ";",
        "csv.quotes": false()
    })
};

(: === Quoting === :)

declare
    %test:assertEquals('a,b
c,d
')
function csv:no-quotes() {
    serialize([["a","b"],["c","d"]], map {
        "method": "csv",
        "csv.quotes": false()
    })
};

declare
    %test:assertTrue
function csv:value-with-comma-gets-quoted() {
    let $result := serialize([["hello, world","test"]], map {
        "method": "csv",
        "csv.quotes": false()
    })
    return contains($result, '"hello, world"')
};

declare
    %test:assertTrue
function csv:value-with-quote-gets-escaped() {
    let $result := serialize([["say ""hello""","test"]], map {
        "method": "csv"
    })
    return contains($result, '""hello""')
};

(: === Sequence of maps === :)

declare
    %test:assertTrue
function csv:maps-produces-output() {
    let $result := serialize((
        map { "name": "Alice", "age": "30" },
        map { "name": "Bob", "age": "25" }
    ), map { "method": "csv" })
    return string-length($result) > 0
};

declare
    %test:assertTrue
function csv:maps-with-header() {
    let $result := serialize((
        map { "name": "Alice", "age": "30" },
        map { "name": "Bob", "age": "25" }
    ), map { "method": "csv", "csv.header": true() })
    (: Header row should contain the key names :)
    return contains($result, "age") and contains($result, "name")
};

(: === XML table === :)

declare
    %test:assertTrue
function csv:xml-table() {
    let $result := serialize(
        <csv>
            <record><field>a</field><field>b</field></record>
            <record><field>c</field><field>d</field></record>
        </csv>,
        map { "method": "csv" }
    )
    return contains($result, "a") and contains($result, "d")
};

(: === Single atomic item === :)

declare
    %test:assertTrue
function csv:single-string() {
    let $result := serialize("hello", map { "method": "csv" })
    return contains($result, "hello")
};

(: === Value with newline gets quoted === :)

declare
    %test:assertTrue
function csv:value-with-newline-gets-quoted() {
    let $result := serialize([["line1&#10;line2", "test"]], map {
        "method": "csv",
        "csv.quotes": false()
    })
    return contains($result, '"line1')
};
