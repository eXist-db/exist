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

module namespace fnewi="http://exist-db.org/xquery/test/function_element_with_id";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fnewi:data :=
          document{
            <employee xml:id="ID21256"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xmlns:xs="http://www.w3.org/2001/XMLSchema">
               <empnr xsi:type="xs:ID">E21256</empnr>
               <first>John</first>
               <last>Brown</last>
            </employee>
          };

declare
    %test:assertEquals("employee")
function fnewi:id-with-is-id-attribute() {
    $fnewi:data/element-with-id('ID21256')/name()
};

declare
    %test:assertEquals("employee")
function fnewi:id-with-is-id-type() {
    $fnewi:data/element-with-id('E21256')/name()
};
