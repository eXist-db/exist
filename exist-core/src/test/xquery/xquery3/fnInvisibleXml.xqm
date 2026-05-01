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
 : Tests for fn:invisible-xml().
 :)
module namespace ixml = "http://exist-db.org/xquery/test/invisible-xml";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $ixml:date-grammar := "date = year, -'-', month, -'-', day.
year = digit, digit, digit, digit.
month = digit, digit.
day = digit, digit.
-digit = '0'|'1'|'2'|'3'|'4'|'5'|'6'|'7'|'8'|'9'.";

declare
    %test:assertEquals('<date><year>2023</year><month>10</month><day>31</day></date>')
function ixml:date-parse() {
    let $parser := fn:invisible-xml($ixml:date-grammar, map {})
    return $parser("2023-10-31")
};

declare
    %test:assertEquals('<date><year>2024</year><month>01</month><day>15</day></date>')
function ixml:date-parse-different-input() {
    let $parser := fn:invisible-xml($ixml:date-grammar, map {})
    return $parser("2024-01-15")
};

declare
    %test:assertError("FOIX0001")
function ixml:invalid-grammar() {
    fn:invisible-xml("this is not a valid grammar !!!", map {})
};

declare
    %test:assertError("FOIX0002")
function ixml:parse-error() {
    let $parser := fn:invisible-xml($ixml:date-grammar, map {"fail-on-error": true()})
    return $parser("not-a-date")
};

declare
    %test:assertEquals('<s/>')
function ixml:xml-grammar-element() {
    let $grammar := parse-xml("<ixml><rule name='s'/></ixml>")/*
    return invisible-xml($grammar)("")
};

declare
    %test:assertEquals('<date><year>2023</year><month>10</month><day>31</day></date>')
function ixml:xml-grammar-complex() {
    let $xml := concat(
        "<ixml><rule name='date'><alt><nonterminal name='year'/>",
        "<literal tmark='-' string='-'/><nonterminal name='month'/>",
        "<literal tmark='-' string='-'/><nonterminal name='day'/></alt></rule>",
        "<rule name='year'><alt><nonterminal name='d'/><nonterminal name='d'/>",
        "<nonterminal name='d'/><nonterminal name='d'/></alt></rule>",
        "<rule name='month'><alt><literal string='0'/><nonterminal name='d'/></alt>",
        "<alt><literal string='1'/><inclusion><member string='0'/><member string='1'/>",
        "<member string='2'/></inclusion></alt></rule>",
        "<rule name='day'><alt><inclusion><member string='0'/><member string='1'/>",
        "<member string='2'/></inclusion><nonterminal name='d'/></alt>",
        "<alt><literal string='3'/><inclusion><member string='0'/>",
        "<member string='1'/></inclusion></alt></rule>",
        "<rule mark='-' name='d'><alt><inclusion>",
        "<member from='0' to='9'/></inclusion></alt></rule></ixml>")
    let $grammar := parse-xml($xml)/*
    return invisible-xml($grammar)("2023-10-31")
};

declare
    %test:assertTrue
function ixml:reuse-parser() {
    let $parser := fn:invisible-xml($ixml:date-grammar, map {})
    let $d1 := $parser("2023-10-31")
    let $d2 := $parser("2024-01-15")
    return $d1/date/year = "2023" and $d2/date/year = "2024"
};
