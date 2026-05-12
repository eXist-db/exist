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
 : Correctness regression tests for the {@code preceding::*} half of issue #2129.
 : The K-bounded sliding window in {@code LocationStep.PrecedingFilter} must
 : preserve the original axis semantics: ancestor exclusion, axis-order
 : positional predicates, and identical result counts/values regardless of
 : where the reference node sits in the document.
 :
 : The companion performance benchmark lives in
 : {@code exist-core-jmh/.../PrecedingAxisBenchmark.java}.
 :)
module namespace pa = "http://exist-db.org/xquery/test/preceding-axis";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $pa:collection := "/db/preceding-axis-test";
declare variable $pa:small-doc := $pa:collection || "/words-small.xml";
declare variable $pa:tiny-doc := $pa:collection || "/tiny.xml";
declare variable $pa:ancestors-doc := $pa:collection || "/ancestors.xml";

declare
    %test:setUp
function pa:setup() {
    let $col := xmldb:create-collection("/db", "preceding-axis-test")
    let $small :=
        document {
            <words>{
                for $i in (1 to 50) return <w xml:id="{$i}">{$i}</w>
            }</words>
        }
    let $tiny :=
        document {
            <words>{
                for $i in (1 to 10) return <w xml:id="{$i}">{$i}</w>
            }</words>
        }
    let $ancestors :=
        document {
            <root><a/><b><c><d><target/></d></c></b></root>
        }
    return (
        xmldb:store($col, "words-small.xml", $small),
        xmldb:store($col, "tiny.xml", $tiny),
        xmldb:store($col, "ancestors.xml", $ancestors)
    )
};

declare
    %test:tearDown
function pa:cleanup() {
    xmldb:remove($pa:collection)
};

declare
    %test:assertEquals("5 6 7 8 9")
function pa:reproducer-output-at-early-position() {
    let $w := doc($pa:small-doc)//w[@xml:id = "10"]
    return
        string-join(
            for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text(),
            " "
        )
};

declare
    %test:assertEquals("20 21 22 23 24")
function pa:reproducer-output-at-midpoint() {
    let $w := doc($pa:small-doc)//w[@xml:id = "25"]
    return
        string-join(
            for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text(),
            " "
        )
};

declare
    %test:assertEquals("40 41 42 43 44")
function pa:reproducer-output-at-late-position() {
    let $w := doc($pa:small-doc)//w[@xml:id = "45"]
    return
        string-join(
            for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text(),
            " "
        )
};

(:~
 : {@code preceding::} excludes ancestors of the reference node even though
 : they precede the reference in document order. Only the sibling {@code a}
 : precedes {@code target} on the preceding axis.
 :)
declare
    %test:assertEquals("a")
function pa:preceding-excludes-ancestors() {
    for $n in doc($pa:ancestors-doc)//target/preceding::*
    return name($n)
};

(:~
 : From {@code w[5]} on a flat doc of 10 elements, the k-th preceding element
 : in axis order (reverse document order) is {@code w[5-k]}.
 :)
declare
    %test:assertEquals("4", "3", "2", "1")
function pa:preceding-positional-predicate-returns-kth-closest() {
    let $w := doc($pa:tiny-doc)//w[@xml:id = "5"]
    return
        for $k in 1 to 4
        return $w/preceding::*[$k]/text()
};
