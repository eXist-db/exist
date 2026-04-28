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
xquery version "4.0";

(:~
 : XQuery 4.0 fn:xml-to-json behaviour: PR1455 number lexical preservation
 : and PR1059 unknown-option rejection.
 :)
module namespace xtj4="http://exist-db.org/xquery/test/xq4-xml-to-json";

declare default element namespace "http://www.w3.org/2005/xpath-functions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: ===== PR1455: preserve number lexical form ===== :)

declare
    %test:arg("n", "1.234e10") %test:assertEquals('1.234e10')
    %test:arg("n", "1E6") %test:assertEquals('1E6')
    %test:arg("n", "10.") %test:assertEquals('10.0')
    %test:arg("n", ".01E02") %test:assertEquals('0.01E02')
    %test:arg("n", "+1.E+02") %test:assertEquals('1.0E+02')
    %test:arg("n", "+1.e+02") %test:assertEquals('1.0e+02')
    %test:arg("n", "00.01E02") %test:assertEquals('0.01E02')
    %test:arg("n", "-001.15e01") %test:assertEquals('-1.15e01')
    %test:arg("n", "-000") %test:assertEquals('-0')
    %test:arg("n", "000000000") %test:assertEquals('0')
    %test:arg("n", "-0e0") %test:assertEquals('-0e0')
    %test:arg("n", "-1E-6") %test:assertEquals('-1E-6')
    %test:arg("n", "23.") %test:assertEquals('23.0')
function xtj4:xml-to-json-preserve-lexical($n as xs:string) as xs:string {
    fn:xml-to-json(<number>{$n}</number>)
};

(: NaN/Infinity rejected even in XQ40 :)
declare
    %test:arg("n", "NaN") %test:assertError("FOJS0006")
    %test:arg("n", "INF") %test:assertError("FOJS0006")
    %test:arg("n", "-INF") %test:assertError("FOJS0006")
function xtj4:xml-to-json-reject-non-finite($n as xs:string) as xs:string {
    fn:xml-to-json(<number>{$n}</number>)
};

(: ===== PR1059: reject unknown options ===== :)

declare
    %test:assertError("FOJS0005")
function xtj4:xml-to-json-unknown-option-rejected() as xs:string {
    fn:xml-to-json(<null/>, map{'outdent': true()})
};

declare
    %test:assertEquals("null")
function xtj4:xml-to-json-empty-options-allowed() as xs:string {
    fn:xml-to-json(<null/>, ())
};
