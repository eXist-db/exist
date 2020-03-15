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
 : Test various F+O functions
 :)
module namespace jsonxml="http://exist-db.org/xquery/test/jsonxml";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare
    %test:assertEquals("<fn:map xmlns:fn='http://www.w3.org/2005/xpath-functions'><fn:number key='x'>1</fn:number><fn:array key='y'><fn:number>3</fn:number><fn:number>4</fn:number><fn:number>5</fn:number></fn:array></fn:map>")
function jsonxml:json-to-xml-1() {
    json-to-xml('{"x": 1, "y": [3,4,5]}')
};


declare
    %test:assertEquals("<map xmlns='http://www.w3.org/2005/xpath-functions'><string key='x'>\</string><string key='y'>%</string></map>")
function jsonxml:json-to-xml-2() {
    json-to-xml('{"x": "\\", "y": "\u0025"}')
};

declare
    %test:pending("not implemented yet")
    %test:assertEquals("<map xmlns='http://www.w3.org/2005/xpath-functions'><string escaped='true' key='x'>\\</string><string key='y'>%</string></map>")
function jsonxml:json-to-xml-3() {
    json-to-xml('{"x": "\\", "y": "\u0025"}', map{'escape': true()})
};


declare
    %test:assertError("err:FOJS0001")
function jsonxml:json-to-xml-error-1() {
    json-to-xml('{"x":')
};


declare
    %test:pending("not implemented yet")
    %test:assertError("err:FOJS0005")
function jsonxml:json-to-xml-error-2() {
    json-to-xml('{"x": "\\", "y": "\u0025"}', map{'escape': 'invalid-value'})
};





