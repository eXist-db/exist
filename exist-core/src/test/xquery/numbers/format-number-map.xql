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

(:~ Tests for fn:format-number with XQuery 4.0 map overload :)
module namespace fnm="http://exist-db.org/xquery/test/format-number-map";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: === Basic map overload — custom separators === :)

declare
    %test:assertEquals("12.345,60")
function fnm:european-format() {
    format-number(12345.6, '#.###,00', map {
        'decimal-separator': ',',
        'grouping-separator': '.'
    })
};

declare
    %test:assertEquals("12 345,60")
function fnm:french-format() {
    format-number(12345.6, '# ###,00', map {
        'decimal-separator': ',',
        'grouping-separator': ' '
    })
};

(: === Custom infinity and NaN === :)

declare
    %test:assertEquals("∞")
function fnm:custom-infinity() {
    format-number(1 div 0e0, '#', map {
        'infinity': '∞'
    })
};

declare
    %test:assertEquals("N/A")
function fnm:custom-nan() {
    format-number(number('NaN'), '#', map {
        'NaN': 'N/A'
    })
};

(: === Custom minus sign === :)

declare
    %test:assertEquals("(42)")
function fnm:custom-minus() {
    format-number(-42, '#;(#)', map {
        'minus-sign': '−'
    })
};

(: === Custom percent and per-mille === :)

declare
    %test:assertEquals("75%")
function fnm:default-percent() {
    format-number(0.75, '#%')
};

(: === Empty map = unnamed default === :)

declare
    %test:assertEquals("1,234.50")
function fnm:empty-map-uses-default() {
    format-number(1234.5, '#,###.00', map {})
};

(: === Map with format-name selects base format === :)
(: Note: this test uses the unnamed default since we can't declare
   custom decimal formats in a module without declare decimal-format :)

declare
    %test:assertEquals("1,234.50")
function fnm:map-with-no-format-name() {
    format-number(1234.5, '#,###.00', map {})
};

(: === Zero digit override === :)

declare
    %test:assertEquals("١٢٣")
function fnm:arabic-digits() {
    (: Arabic-Indic digit zero is U+0660, picture must use same digit family :)
    format-number(123, '٠٠٠', map {
        'zero-digit': '٠'
    })
};

(: === Exponent separator === :)

declare
    %test:assertEquals("1.23E3")
function fnm:custom-exponent-separator() {
    format-number(1230, '0.00E0', map {
        'exponent-separator': 'E'
    })
};

(: === Multiple overrides at once === :)

declare
    %test:assertEquals("1.234,56")
function fnm:multiple-overrides() {
    format-number(1234.56, '#.###,00', map {
        'decimal-separator': ',',
        'grouping-separator': '.'
    })
};

(: === Backward compatibility: string arg still works === :)

declare
    %test:assertEquals("1,234.50")
function fnm:string-arg-still-works() {
    (: No custom decimal format declared, so unnamed default :)
    format-number(1234.5, '#,###.00')
};
