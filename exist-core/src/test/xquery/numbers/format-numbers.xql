xquery version "3.0";

module namespace fd="http://exist-db.org/xquery/test/format-numbers";

declare namespace test="http://exist-db.org/xquery/xqsuite";


(:~
 : A picture-string must not contain more than one instance of the
 : pattern-separator character
 :)
declare
    %test:args("0;0;0")
    %test:assertError("FODF1310")
function fd:invalid-picture-multiple-pattern-separator($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture must not contain more than one instance of the
 : decimal-separator character
 :)
declare
    %test:args("0.0.0")
    %test:assertError("FODF1310")
    %test:args("0.0.0;0.0")
    %test:assertError("FODF1310")
    %test:args("0.0;0.0.0")
    %test:assertError("FODF1310")
function fd:invalid-picture-multiple-decimal-separator($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture must not contain more than one instance of the percent
 : or per-mille characters, and it must not contain one of each
 :)
declare
    %test:args("0%%")
    %test:assertError("FODF1310")
    %test:args("0%%;0")
    %test:assertError("FODF1310")
    %test:args("0;0%%")
    %test:assertError("FODF1310")
    %test:args("0‰‰;0")
    %test:assertError("FODF1310")
    %test:args("0;0‰‰")
    %test:assertError("FODF1310")
    %test:args("0;0‰‰")
    %test:assertError("FODF1310")
    %test:args("0%‰")
    %test:assertError("FODF1310")
    %test:args("0‰%")
    %test:assertError("FODF1310")
    %test:args("0%‰;0")
    %test:assertError("FODF1310")
    %test:args("0‰%;0")
    %test:assertError("FODF1310")
    %test:args("0;0%‰")
    %test:assertError("FODF1310")
    %test:args("0;0‰%")
    %test:assertError("FODF1310")
function fd:invalid-picture-multiple-percent-permille($picture) {
    format-number(12345.6, $picture)
};

(:~
 : The mantissa part of a sub-picture must contain at least one character that
 : is either an optional digit character or a member of the decimal digit family
 :)
declare
    %test:args(".")
    %test:assertError("FODF1310")
    %test:args("0.0;.")
    %test:assertError("FODF1310")
    %test:args(".;0.0")
    %test:assertError("FODF1310")
function fd:invalid-picture-mantissa($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture must not contain a passive character that is preceded by an
 : active character and that is followed by another active character.
 :)
declare
    %test:args("#a#;#")
    %test:assertError("FODF1310")
    %test:args("#;#a#")
    %test:assertError("FODF1310")
function fd:invalid-picture-active-passive-active($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture must not contain a grouping-separator character that appears
 : adjacent to a decimal-separator character, or in the absence of
 : decimal-separator character, at the end of the integer part.
 :)
declare
    %test:args("###,.;#")
    %test:assertError("FODF1310")
    %test:args("#;###,.")
    %test:assertError("FODF1310")
    %test:args("###.,;#")
    %test:assertError("FODF1310")
    %test:args("#;###.,")
    %test:assertError("FODF1310")
    %test:args("###,")
    %test:assertError("FODF1310")
    %test:args("###,;#")
    %test:assertError("FODF1310")
    %test:args("#;###,")
    %test:assertError("FODF1310")
function fd:invalid-picture-grouping-separator-1($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture must not contain two adjacent instances of the
 : grouping-separator character.
 :)
declare
    %test:args("###,,###;#")
    %test:assertError("FODF1310")
    %test:args("#;###,,###")
    %test:assertError("FODF1310")
function fd:invalid-picture-grouping-separator-2($picture) {
    format-number(12345.6, $picture)
};

(:~
 : The integer part of a sub-picture must not contain a member of the
 : decimal digit family that is followed by an instance of the
 : optional digit character. The fractional part of a sub-picture
 : must not contain an instance of the optional digit character that
 : is followed by a member of the decimal digit family.
 :)
declare
    %test:args("1#;#")
    %test:assertError("FODF1310")
    %test:args("#;1#")
    %test:assertError("FODF1310")
    %test:args("##.#1;#")
    %test:assertError("FODF1310")
    %test:args("#;##.#1")
    %test:assertError("FODF1310")
function fd:invalid-picture-decimal-digit-and-optional-digit($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A character that matches the exponent-separator property is treated as an
 : exponent-separator-sign if it is both preceded and followed within the
 : sub-picture by an active character. Otherwise, it is treated as a passive
 : character. A sub-picture must not contain more than one character that is
 : treated as an exponent-separator-sign.
 :)
declare
    %test:args("###ee##;#")
    %test:assertError("FODF1310")
    %test:args("#;###ee##")
    %test:assertError("FODF1310")
function fd:invalid-picture-exponent-separator-1($picture) {
    format-number(12345.6, $picture)
};

(:~
 : A sub-picture that contains a percent or per-mille character must not
 : contain a character treated as an exponent-separator-sign.
 :)
declare
    %test:args("###e%##;#")
    %test:assertError("FODF1310")
    %test:args("#;###e%##")
    %test:assertError("FODF1310")
    %test:args("###e‰##;#")
    %test:assertError("FODF1310")
    %test:args("#;###e‰##")
    %test:assertError("FODF1310")
    %test:args("#.#%e;#")
    %test:assertError("FODF1310")
    %test:args("#;#.#%e")
    %test:assertError("FODF1310")
    %test:args("#.#‰e;#")
    %test:assertError("FODF1310")
    %test:args("#;#.#‰e")
    %test:assertError("FODF1310")
function fd:invalid-picture-exponent-separator-2($picture) {
    format-number(12345.6, $picture)
};

(:~
 : If a sub-picture contains a character treated as an exponent-separator-sign
 : then this must be followed by one or more characters that are members of
 : the decimal digit family, and it must not be followed by any active
 : character that is not a member of the decimal digit family.
 :)
declare
    %test:args("##e.;#")
    %test:assertError("FODF1310")
    %test:args("#;##e.")
    %test:assertError("FODF1310")
function fd:invalid-picture-exponent-separator-3($picture) {
    format-number(12345.6, $picture)
};

declare
    %test:pending("It is not clear why this should be invalid according to the XQ3.1 spec")
    %test:args("12345.6")
    %test:assertError("FODF1310")
function fd:invalid-picture($number as xs:numeric) {
    format-number($number, "#.###,00")
};

declare
    %test:args(1000000)
    %test:args("000,000,000")
    %test:assertEquals("001,000,000")
function fd:grouping-integer($number as xs:integer, $picture) {
    format-number($number, $picture)
};

declare
    %test:args(1.234567890)
    %test:args("0.000,000,000")
    %test:assertEquals("1.234,567,890")
function fd:grouping-fraction($number as xs:double, $picture) {
    format-number($number, $picture)
};

declare
    %test:args("12345.6")
    %test:args("#,###.##")
    %test:assertEquals("12,345.6")
function fd:simple-number-1($number as numeric, $picture) {
    format-number($number, $picture)
};

declare
    %test:args(87504.4812)
    %test:args("000,000.000000")
    %test:assertEquals("087,504.481200")
function fd:simple-number-2($number, $picture) {
    format-number($number, $picture)
};

declare
    %test:args(12345.6)
    %test:args("#.###,##")
    %test:assertEquals("12345.6")
function fd:simple-number-3($number as numeric, $picture) {
    format-number($number, $picture)
};

declare
    %test:args(14)
    %test:args("00%")
    %test:assertEquals("1400%")
function fd:simple-number-integer-percent($number as xs:integer, $picture) {
    format-number($number, $picture)
};

declare
    %test:args(0.14)
    %test:args("01%")
    %test:assertEquals("14%")
function fd:simple-number-fractional-percent($number as xs:double, $picture) {
    format-number($number, $picture)
};

declare
    %test:args("#0")
    %test:assertEquals("0")
    %test:args("#0.0")
    %test:assertEquals("0.0")
    %test:args("#0.00")
    %test:assertEquals("0.00")
    %test:args("#0.000")
    %test:assertEquals("0.000")
    %test:args("#0.0#")
    %test:assertEquals("0.0")
function fd:decimal-zeros($picture as xs:string) {
    format-number(0, $picture)
};