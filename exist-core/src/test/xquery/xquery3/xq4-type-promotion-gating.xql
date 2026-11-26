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

module namespace tpg="http://exist-db.org/xquery/test/type-promotion-gating";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Helper function that expects xs:anyURI :)
declare function tpg:expect-uri($uri as xs:anyURI) as xs:string {
    string($uri)
};

(: In XQuery 3.1, string -> anyURI coercion should NOT work :)
declare
    %test:assertError("XPTY0004")
function tpg:string-to-anyuri-rejected-in-31() {
    tpg:expect-uri("http://example.com")
};
