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
 : Test for base-uri
 :)
module namespace baseuri="http://exist-db.org/xquery/test/baseuri";

import module namespace test="http://exist-db.org/xquery/xqsuite"
                             at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $baseuri:DOCUMENT := <a xml:base="http://a">
                                    <b xml:base="http://b">foo</b>
                                    <c>bar</c>
                                    <d xml:base="d">snafuu</d>
                                    <e xml:base="/e">snafuu</e>
                                  </a>;

declare
    %test:setUp
function baseuri:store() {
    let $col := xmldb:create-collection("/db", "base-uri")
    return
        (
            xmldb:store($col, "test.xml", $baseuri:DOCUMENT)
        )
};

declare
    %test:tearDown
function baseuri:cleanup() {
    xmldb:remove("/db/base-uri")
};

declare
    %test:assertEmpty
function baseuri:attribute_empty() {
     (attribute anAttribute{"attribute value"})/fn:base-uri()
};

declare
    %test:assertEmpty
function baseuri:pi_empty() {
     fn:base-uri(processing-instruction {"PItarget"} {"PIcontent"})
};

declare
    %test:assertEmpty
function baseuri:textnode_empty() {
     fn:base-uri(text {"A Text Node"})
};
