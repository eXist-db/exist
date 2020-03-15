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
xquery version "3.0";

(:~
 : Tests textual representation of datatypes
 :)
module namespace tps="http://exist-db.org/test/types";

import module namespace inspect="http://exist-db.org/xquery/inspection" at "java:org.exist.xquery.functions.inspect.InspectionModule";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("map(*)")
function tps:map_type() {
    data(inspect:inspect-function(map:entry#2)//returns/@type)
};

declare
    %test:assertEquals("empty-sequence()")
function tps:empty_sequence() {
    data(inspect:inspect-function(fn:error#2)//returns/@type)
};

declare
    %test:assertEquals("document-node()")
function tps:document_node() {
    data(inspect:inspect-function(fn:doc#1)//returns/@type)
};

declare
    %test:assertEquals("xs:string")
function tps:string() {
    data(inspect:inspect-function(fn:format-date#2)//returns/@type)
};

declare
    %test:assertEquals("xs:integer")
function tps:integer() {
    data(inspect:inspect-function(fn:function-arity#1)//returns/@type)
};

declare
    %test:assertEquals("function(*)")
function tps:function() {
    data(inspect:inspect-function(fn:function-lookup#2)//returns/@type)
};

declare
    %test:assertEquals("xs:QName")
function tps:xqname() {
    data(inspect:inspect-function(fn:function-name#1)//returns/@type)
};

declare
    %test:assertEquals("array(*)")
function tps:array() {
    data(inspect:inspect-function(array:append#2)//returns/@type)
};

