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

module namespace px="http://exist-db.org/xquery/test/parse-xml";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("") %test:assertFalse
    %test:args(" ") %test:assertFalse
    %test:args("He was <i>so</i> kind") %test:assertFalse
    %test:args("<a>a</a><b>b</b>") %test:assertFalse
    %test:args('<a>no') %test:assertFalse
    %test:args('<?xml version="1.0" encoding="utf8"?><a/>') %test:assertTrue
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>') %test:assertTrue
function px:return-type($in as xs:string) as xs:boolean {
    try {
        fn:parse-xml($in) instance of document-node()
    } catch err:FODC0006 {
        false()
    }
};

declare
    %test:assertTrue
function px:empty-returns-empty() as xs:boolean {
    fn:parse-xml(()) instance of empty-sequence()
};

declare
    %test:args("") %test:assertEquals(0)
    %test:args(" ") %test:assertEquals(0)
    %test:args("He was <i>so</i> kind") %test:assertEquals(0)
    %test:args("<a>a</a><b>b</b>") %test:assertEquals(0)
    %test:args('<a>no') %test:assertEquals(0)
    %test:args('<?xml version="1.0" encoding="utf8"?><a/>') %test:assertEquals(1)
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a/>') %test:assertEquals(1)
function px:node-count($in as xs:string) as xs:integer {
    try {
        count(fn:parse-xml($in)/node())
    } catch err:FODC0006 {
        0
    }
};

declare
    %test:args('<text>VALID</text>')
    %test:assertEquals("VALID", 0, 0)
    %test:args('<a>no')
    %test:assertEquals("err:FODC0006", 75, 9)
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a>VALID</a>')
    %test:assertEquals("VALID", 0, 0)
function px:error-code-and-location($in as xs:string) as xs:anyAtomicType* {
    try {
        fn:parse-xml($in)/string(), 0, 0
    } catch err:FODC0006 {
        $err:code, $err:line-number, $err:column-number
    }
};

declare
    %test:args('<text>Valid document</text>')
    %test:assertEquals("Valid document")
    %test:args('<a>no')
    %test:assertEquals("String passed to fn:parse-xml is not a well-formed XML document. parse-xml failed with: XML document structures must start and end within the same entity.")
    %test:args('<?xml version="1.0" encoding="utf8" standalone="yes"?><a>Valid document</a>')
    %test:assertEquals("Valid document")
function px:error-description($in as xs:string) as xs:string {
    try {
        fn:parse-xml($in)/string()
    } catch err:FODC0006 {
        $err:description
    }
};
