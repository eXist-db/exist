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
 : Reference checks: every fn:matches the range index answers must give the answer of fn:matches over
 : the same values without an index. The patterns and flags are generated from building blocks, the
 : values cover the characters on which Lucene's and XPath's regular expressions are known to differ
 : (case variants, newlines, digits and word characters outside ASCII, supplementary characters, and
 : Lucene's operators). Each indexed query is compiled with literal arguments, so that the optimizer
 : rewrites it to the range index; the reference is the same fn:matches call on in-memory copies.
 :)
module namespace rm = "http://exist-db.org/xquery/test/range/reference-matches";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $rm:COLLECTION := "/db/reference-matches";
declare variable $rm:CONFIG := "/db/system/config" || $rm:COLLECTION;

declare variable $rm:VALUES := (
    "ham", "Ham", "HAM", "hamburg", "bacon", "",
    "a" || codepoints-to-string(10) || "b", "tab" || codepoints-to-string(9) || "here",
    "k", "K", codepoints-to-string(8490), "s", "S", codepoints-to-string(383),
    "i", "I", codepoints-to-string(304), codepoints-to-string(305), "straße", "STRASSE",
    "x_y", "x y", "123", codepoints-to-string((1633, 1634, 1635)),
    codepoints-to-string(128512), "a" || codepoints-to-string(128512) || "b", codepoints-to-string(119964),
    "$5", "a.b", "a*b", "a&amp;b", "~x", "#1", "@home", "&lt;3&gt;", '"q"', "a\b", "aaa", "ab", "ba"
);

(: the building blocks: literals, classes, quantifiers, Lucene's operators, constructs Lucene lacks :)
declare variable $rm:ATOMS := (
    "ham", "a", "b", "k", "s", "i", ".", ".*", ".+", "a?", "a{2}", "\d", "\w", "\s", "\S", "[a-c]", "[^a]",
    "[a-z-[aeiou]]", "\p{Lu}", "\P{L}", "(a|b)", "a|b", "&amp;", "~", "#", "@", "&lt;3&gt;", '"', "\.", "\\",
    "\n", "\t", "x y", "ß"
);

(: the blocks the range index serves itself, not handing them to fn:matches :)
declare variable $rm:SERVED := (
    "ham", "a", "b", "k", "s", "i", ".", ".*", ".+", "a?", "a{2}", "[a-c]", "[^a]", "(a|b)", "&amp;", "~", "#", "@",
    "&lt;3&gt;", '"', "\.", "\\", "x y", "ß"
);

declare variable $rm:FLAGS := ("", "i", "s", "m", "x", "q", "is");

declare function rm:patterns($atoms as xs:string*) as xs:string* {
    distinct-values((
        for $a in $atoms
        return ($a, "^" || $a, $a || "$", "^" || $a || "$"),
        for $a in $atoms[position() le 12], $b in $atoms[position() le 12]
        return ($a || $b, "^" || $a || ".*" || $b || "$")
    ))
};

declare
    %test:setUp
function rm:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($rm:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($rm:COLLECTION, "/db/"))
    let $_ := xmldb:store($rm:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><range><create qname="v" type="xs:string"/></range></index>
        </collection>)
    return xmldb:store($rm:COLLECTION, "values.xml", <values>{ $rm:VALUES ! <v>{ . }</v> }</values>)
};

declare
    %test:tearDown
function rm:teardown() {
    xmldb:remove($rm:COLLECTION),
    xmldb:remove($rm:CONFIG)
};

declare %private function rm:literal($s as xs:string) as xs:string {
    "'" || replace(replace($s, "&amp;", "&amp;amp;"), "'", "''") || "'"
};

(: the answer, or the error code: an invalid pattern must be an error on both sides :)
declare %private function rm:indexed($pattern as xs:string, $flags as xs:string) as xs:string {
    try {
        string(util:eval("count(collection(" || rm:literal($rm:COLLECTION) || ")//v[matches(., "
            || rm:literal($pattern) || ", " || rm:literal($flags) || ")])"))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

declare %private function rm:reference($pattern as xs:string, $flags as xs:string) as xs:string {
    try {
        string(count($rm:VALUES[matches(., $pattern, $flags)]))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

(: the differences, as "pattern | flags | index | reference" :)
declare %private function rm:differences($patterns as xs:string*, $flags as xs:string*) as xs:string* {
    for $pattern in $patterns, $f in $flags
    let $indexed := rm:indexed($pattern, $f)
    let $reference := rm:reference($pattern, $f)
    where $indexed ne $reference
    return string-join(($pattern, $f, $indexed, $reference), " | ")
};

(: the patterns the range index serves itself, under the flags it serves (none and i) :)
declare
    %test:assertEmpty
function rm:index-matches-like-fn-matches() {
    rm:differences(rm:patterns($rm:SERVED), ("", "i"))
};

(: every pattern under every flag, including those the index hands to fn:matches; fn:matches' own index path
   evaluates them with Java's regular expressions, see https://github.com/eXist-db/exist/pull/6747 :)
declare
    %test:assertEmpty
function rm:index-or-fallback-matches-like-fn-matches() {
    rm:differences(rm:patterns($rm:ATOMS), $rm:FLAGS)
};

(: not vacuous: the served patterns match different numbers of values, so the comparison tells them apart :)
declare
    %test:assertTrue
function rm:the-reference-is-not-trivial() {
    let $answers := for $pattern in rm:patterns($rm:SERVED), $f in ("", "i") return rm:reference($pattern, $f)
    return count(distinct-values($answers)) gt 10 and count($answers[. ne "0"]) gt 100
};
