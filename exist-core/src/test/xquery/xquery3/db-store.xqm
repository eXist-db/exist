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
module namespace xmldb-store="http://exist-db.org/xquery/test/xmldb-store";
declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("/db/image.jpg")
function xmldb-store:test-store-from-url() {
    xmldb:store(
        '/db',
        'image.jpg',
        (: probably we should mock this URL with wiremock :)
        xs:anyURI('https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png'),
        'image/png'
    )
};


