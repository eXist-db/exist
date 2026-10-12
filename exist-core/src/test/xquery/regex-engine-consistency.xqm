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
 : fn:matches, fn:replace, fn:tokenize and fn:analyze-string run on one regular-expression engine,
 : Saxon's: the constructs where java.util.regex would answer differently, and the flag syntax --
 : XPath's smixq, and ;j to opt into Java's engine explicitly, with nothing else admitted after the
 : semicolon.
 :)
module namespace rec="http://exist-db.org/xquery/test/regex-engine-consistency";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $rec:COLLECTION := "/db/regex-engine-consistency";

declare
    %test:setUp
function rec:setup() {
    xmldb:create-collection("/db/system/config/db", "regex-engine-consistency"),
    xmldb:store("/db/system/config/db/regex-engine-consistency", "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><create qname="e" type="xs:string"/></index>
        </collection>),
    xmldb:create-collection("/db", "regex-engine-consistency"),
    xmldb:store($rec:COLLECTION, "data.xml", <r><e>e</e><e>b</e></r>)
};

declare
    %test:tearDown
function rec:tear-down() {
    xmldb:remove($rec:COLLECTION),
    xmldb:remove("/db/system/config/db/regex-engine-consistency")
};

(: ---- XPath 3.1 semantics where java.util.regex differs ---- :)

(:~
 : XPath's \s is #x20, \t, \n and \r. Java's also includes form feed and vertical tab, but neither
 : is an XML 1.0 character, so that divergence cannot be reached from a query.
 :)
declare
    %test:assertEquals("true", "true", "true", "true")
function rec:whitespace-class-matches-xml-whitespace() {
    for $cp in (32, 9, 10, 13)
    return matches(concat('a', codepoints-to-string($cp), 'b'), '^a\sb$')
};

declare
    %test:assertTrue
function rec:digit-class-is-unicode() {
    matches(codepoints-to-string(1635), '^\d$')
};

declare
    %test:assertEquals("false", "true")
function rec:word-class-is-unicode-and-excludes-underscore() {
    matches('_', '^\w$'), matches(codepoints-to-string(1078), '^\w$')
};

declare
    %test:assertFalse
function rec:dollar-does-not-match-before-a-trailing-newline() {
    matches(concat('abc', codepoints-to-string(10)), 'abc$')
};

declare
    %test:assertFalse
function rec:dot-does-not-match-carriage-return() {
    matches(concat('a', codepoints-to-string(13), 'b'), 'a.b')
};

(:~ Java has no subtraction syntax and reads this as a union, matching everything. :)
declare
    %test:assertEquals("false", "true")
function rec:character-class-subtraction() {
    matches('e', '^[a-z-[aeiou]]$'), matches('b', '^[a-z-[aeiou]]$')
};

declare
    %test:assertEquals("false", "true", "true")
function rec:xml-name-escapes-and-block-escapes() {
    matches('1', '^\i$'), matches('-', '^\c$'), matches('a', '^\p{IsBasicLatin}$')
};

declare
    %test:assertTrue
function rec:case-insensitive-final-sigma() {
    matches(codepoints-to-string(931), codepoints-to-string(962), 'i')
};

(: ---- invalid patterns raise FORX0002 instead of answering false ---- :)

(:~ XQTS re00019, re00804, fn-matchesErr-4, fn-matchesErr-5 :)
declare
    %test:assertEquals("err:FORX0002", "err:FORX0002", "err:FORX0002", "err:FORX0002")
function rec:invalid-patterns-are-errors-not-false() {
    try { matches('qwerty', '{1}a') } catch * { string($err:code) },
    try { matches('qwerty', 'a]') } catch * { string($err:code) },
    try { matches('#abc#1', '^((#)abc\1)$') } catch * { string($err:code) },
    try { matches('abcdefghijklmnopq', '(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)(l)((m)(n)(o)(p)(q)\13)$') } catch * { string($err:code) }
};

declare
    %test:assertEquals("err:FORX0002", "err:FORX0002", "err:FORX0002", "err:FORX0002")
function rec:java-only-syntax-is-an-error-without-opting-in() {
    try { matches('a foo b', '\bfoo\b') } catch * { string($err:code) },
    try { replace('a foo b', '\bfoo\b', 'X') } catch * { string($err:code) },
    try { string-join(tokenize('a foo b', '\bfoo\b'), '|') } catch * { string($err:code) },
    try { string(analyze-string('a foo b', '\bfoo\b')) } catch * { string($err:code) }
};

(: ---- flags: XPath's set, plus exactly ;j ---- :)

declare
    %test:assertEquals("true", "a X b", "a | b", "true", "1")
function rec:semicolon-j-selects-java-syntax-everywhere() {
    matches('a foo b', '\bfoo\b', ';j'),
    replace('a foo b', '\bfoo\b', 'X', ';j'),
    string-join(tokenize('a foo b', '\bfoo\b', ';j'), '|'),
    matches('a FOO b', '\bfoo\b', 'i;j'),
    count(analyze-string('a foo b', '\bfoo\b', ';j')//*:match)
};

declare
    %test:assertEquals("err:FORX0001")
function rec:a-bare-j-is-not-a-flag() {
    try { matches('a', 'a', 'j') } catch * { string($err:code) }
};

declare
    %test:assertEquals("err:FORX0001", "err:FORX0001", "err:FORX0001", "err:FORX0001", "err:FORX0001")
function rec:only-j-is-admitted-after-the-semicolon() {
    try { matches('a', 'a', ';x') } catch * { string($err:code) },
    try { matches('a', 'a', ';jz') } catch * { string($err:code) },
    try { matches('a', 'a', ';J') } catch * { string($err:code) },
    try { matches('a', 'a', ';') } catch * { string($err:code) },
    try { string-join(tokenize('a b', ' ', ';n'), '|') } catch * { string($err:code) }
};

declare
    %test:assertEquals("err:FORX0001", "err:FORX0001")
function rec:unknown-xpath-flags-are-still-errors() {
    try { matches('a', 'a', 'p') } catch * { string($err:code) },
    try { matches('a', 'a', 'X') } catch * { string($err:code) }
};

(: ---- fn:tokenize on the shared engine ---- :)

(:~ XQTS fn-tokenize-36 and -38 :)
declare
    %test:assertEquals("err:FORX0003", "err:FORX0003")
function rec:tokenize-pattern-matching-empty-is-an-error() {
    try { string-join(tokenize(concat('Mary', codepoints-to-string(10), 'Jones'), '^', 'm'), '|') } catch * { string($err:code) },
    try { string-join(tokenize(concat('Mary', codepoints-to-string(10), 'Jones'), '^[\s]*$', 'm'), '|') } catch * { string($err:code) }
};

(:~ XQTS fn-tokenize-41 and -42 :)
declare
    %test:assertEquals(0, 0)
function rec:tokenize-of-whitespace-only-is-empty() {
    count(tokenize('   ')), count(tokenize(codepoints-to-string((9, 10, 13, 32, 13, 10, 9))))
};

declare
    %test:assertEquals("a||b", "a|b|", "0", "a|b")
function rec:tokenize-keeps-empty-tokens-between-and-after-separators() {
    string-join(tokenize('a,,b', ','), '|'),
    string-join(tokenize('a,b,', ','), '|'),
    string(count(tokenize('', ','))),
    string-join(tokenize(' a  b '), '|')
};

(: ---- one engine, one answer ---- :)

(:~ Class subtraction: Java reads the class as a union and would match the vowel. :)
declare
    %test:assertEquals("false", "e", "1", "0")
function rec:all-four-functions-agree-on-a-divergent-construct() {
    string(matches('e', '^[a-z-[aeiou]]$')),
    replace('e', '^[a-z-[aeiou]]$', 'X'),
    string(count(tokenize('e', '[a-z-[aeiou]]'))),
    string(count(analyze-string('e', '[a-z-[aeiou]]')//*:match))
};

(:~
 : An indexed predicate gives the same answers as a value: XPath's class subtraction without ;j,
 : and Java's union, which matches both elements, with it.
 :)
declare
    %test:assertEquals(2, 1)
function rec:semicolon-j-applies-to-an-indexed-predicate() {
    count(doc($rec:COLLECTION || "/data.xml")//e[matches(., '^[a-z-[aeiou]]$', ';j')]),
    count(doc($rec:COLLECTION || "/data.xml")//e[matches(., '^[a-z-[aeiou]]$')])
};
