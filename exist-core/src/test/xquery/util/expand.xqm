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

module namespace ue = "http://exist-db.org/xquery/test/util/expand";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace util = "http://exist-db.org/xquery/util";

declare
    %test:assertEquals("true", "true", "bar")
function ue:attribute() as item()+ {
   let $attr := util:expand( attribute foo { "bar" } )
   return
       (
               $attr instance of attribute(),
               node-name($attr) eq xs:QName("foo"),
               string($attr)
       )
};

declare
    %test:assertEquals("true", "true", "bar")
function ue:attributeNs() as item()+ {
    let $attr := util:expand( attribute ue:foo { "bar" } )
    return
        (
                $attr instance of attribute(),
                node-name($attr) eq xs:QName("ue:foo"),
                string($attr)
        )
};

declare
    %test:assertTrue
function ue:comment() as xs:boolean {
    util:expand( comment { "foo" } ) instance of comment()
};

declare
    %test:assertTrue
function ue:document() as xs:boolean {
    util:expand( document { element foo {()} } ) instance of document-node()
};

declare
    %test:assertTrue
function ue:documentNs() as xs:boolean {
    util:expand( document { element ue:foo {()} } ) instance of document-node()
};

declare
    %test:assertEquals("true", "true")
function ue:element() as xs:boolean+ {
    let $elem := util:expand( element foo {()} )
    return
        (
                $elem instance of element(),
                node-name($elem) eq xs:QName("foo")
        )
};

declare
    %test:assertEquals("true", "true")
function ue:elementNs() as xs:boolean+ {
    let $elem := util:expand( element ue:foo {()} )
    return
        (
                $elem instance of element(),
                node-name($elem) eq xs:QName("ue:foo")
        )
};

declare
    %test:assertTrue
function ue:pi() as xs:boolean {
    util:expand( processing-instruction foo { "" } ) instance of processing-instruction()
};

declare
    %test:assertTrue
function ue:text() as xs:boolean {
    util:expand( text { "foo" } ) instance of text()
};