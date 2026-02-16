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

module namespace pxf="http://exist-db.org/xquery/test/parse-xml-fragment";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("") %test:assertFalse
    %test:args(" ") %test:assertTrue
    %test:args("He was <i>so</i> kind") %test:assertTrue
    %test:args("<a>a</a><b>b</b>") %test:assertTrue
    %test:args('<a>no') %test:assertFalse
    %test:args('<?xml version="1.0" encoding="utf8"?><a/>') %test:assertTrue
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>') %test:assertFalse
function pxf:return-type($in as xs:string) as xs:boolean {
    try {
        fn:parse-xml-fragment($in) instance of document-node()
    } catch err:FODC0006 {
        false()
    }
};

declare
    %test:args("") %test:assertEquals(0)
    %test:args(" ") %test:assertEquals(1)
    %test:args("He was <i>so</i> kind") %test:assertEquals(3)
    %test:args("<a>a</a><b>b</b>") %test:assertEquals(2)
    %test:args('<a>no') %test:assertEquals(0)
    %test:args('<?xml version="1.0" encoding="utf8"?><a/>') %test:assertEquals(1)
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>') %test:assertEquals(0)
function pxf:node-count($in as xs:string) as xs:integer {
    try {
        count(fn:parse-xml-fragment($in)/node())
    } catch err:FODC0006 {
        0
    }
};

declare
    %test:args('<text>VALID</text>')
    %test:assertEquals("VALID", 0, 0)
    %test:args('<a>no')
    %test:assertEquals("err:FODC0006", 69, 9)
     %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>')
    %test:assertEquals("err:FODC0006", 69, 9)
function pxf:error-code-and-location($in as xs:string) as xs:anyAtomicType* {
    try {
        fn:parse-xml-fragment($in)/string(), 0, 0
    } catch err:FODC0006 {
        $err:code, $err:line-number, $err:column-number
    }
};

declare
    %test:args('<text>Valid document</text>')
    %test:assertEquals("Valid document")
    %test:args('<a>no')
    %test:assertEquals("String passed to fn:parse-xml is not a well-formed XML document. parse-xml-fragment failed with: The element type ""a"" must be terminated by the matching end-tag ""&lt;/a&gt;"".")
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>')
    %test:assertEquals("String passed to fn:parse-xml is not a well-formed XML document. parse-xml-fragment failed with: Pseudo attribute ""standalone"" not allowed in input fragment")
function pxf:error-description($in as xs:string) as xs:string {
    try {
        fn:parse-xml-fragment($in)/string()
    } catch err:FODC0006 {
        $err:description
    }
};
