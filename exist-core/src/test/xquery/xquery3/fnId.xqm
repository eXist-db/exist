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

module namespace fni="http://exist-db.org/xquery/test/function_id";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fni:data :=
          document{
            <employee xml:id="ID21256"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xmlns:xs="http://www.w3.org/2001/XMLSchema">
               <empnr xsi:type="xs:ID">E21256</empnr>
               <first>John</first>
               <last>Brown</last>
            </employee>
          };

declare variable $fni:ids2 :=
        <IDS2 xmlns = "http://www.w3.org/XQueryTest/ididrefs"
              xmlns:i = "http://www.w3.org/XQueryTest/ididrefs"
              xmlns:xsi = "http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation = "http://www.w3.org/XQueryTest/ididrefs id.xsd">

          <Element-with-ID-attribute id="alpha"><data>derived from Phoenician Aleph</data></Element-with-ID-attribute>
          <Element-with-ID-attribute id="beta"><data>derived from Phoenician Beth</data></Element-with-ID-attribute>
          <Element-with-Restricted-ID-attribute id="gamma"><data>derived from Phoenician Gimel</data></Element-with-Restricted-ID-attribute>
          <Element-with-Restricted-ID-attribute id="delta"><data>derived from Phoenician Daleth</data></Element-with-Restricted-ID-attribute>
          <Element-as-ID>epsilon</Element-as-ID>
          <Element-as-ID>zeta</Element-as-ID>
          <Element-as-Restricted-ID>eta</Element-as-Restricted-ID>
          <Element-as-Restricted-ID>theta</Element-as-Restricted-ID>
          <Element-with-ID-child><id>iota</id><data>Derived from Phoenician Yodh</data></Element-with-ID-child>
          <Element-with-ID-child><id>kappa</id><data>Derived from Phoenician Kaph</data></Element-with-ID-child>
          <Element-with-Restricted-ID-child><id>lambda</id><data>Derived from Phoenician Lamedh</data></Element-with-Restricted-ID-child>
          <Element-with-Restricted-ID-child><id>mu</id><data>Derived from Phoenician Mem</data></Element-with-Restricted-ID-child>
          <Element-with-complex-ID-child><id charmed="false">nu</id><data>Derived from Phoenician Nun</data></Element-with-complex-ID-child>
          <Element-with-ID-list-child><id>xi</id><data>Derived from Phoenician Samekh</data></Element-with-ID-list-child>
          <Element-with-ID-list-child><id>ping pong</id><data>not an id, as not a singleton value</data></Element-with-ID-list-child>
          <Element-with-ID-union-child><id>omicron</id><data>Derived from Phoenician Ayin</data></Element-with-ID-union-child>
          <Element-with-ID-union-child><id>853</id><data>Not an id, wrong member of union</data></Element-with-ID-union-child>

          <IDREF>zeta</IDREF>
          <IDREFS>gamma kappa</IDREFS>
          <IDREF-List>epsilon mu alpha</IDREF-List>
          <IDREF-Union>eta 234 delta</IDREF-Union>
          <Restricted-IDREF>iota</Restricted-IDREF>
          <List-of-Restricted-IDREF>lambda beta iota</List-of-Restricted-IDREF>

          <IDREF-content>zeta</IDREF-content>
          <IDREFS-content>gamma kappa</IDREFS-content>
          <IDREF-List-content>epsilon mu alpha</IDREF-List-content>
          <IDREF-Union-content>eta 234 delta</IDREF-Union-content>
          <Restricted-IDREF-content>iota</Restricted-IDREF-content>
          <List-of-Restricted-IDREF-content>lambda beta iota</List-of-Restricted-IDREF-content>

          <Nillable-IDREF>omicron</Nillable-IDREF>
          <Nillable-IDREF xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>

          <Nillable-ID xsi:nil="true"/>

          <Restricted-NCName-or-IDREF-list>Q</Restricted-NCName-or-IDREF-list>
          <Restricted-NCName-or-IDREF-list>Q omicron</Restricted-NCName-or-IDREF-list>

          <W i:IDREF="epsilon"/>
          <W i:IDREFS="delta eta"/>
          <W i:IDREF-List="zeta iota"/>
          <W i:IDREF-Union="gamma 976 delta alpha"/>
          <W i:Restricted-IDREF="beta"/>
          <W i:List-of-Restricted-IDREF="lambda kappa"/>
          <W i:Restricted-NCName-or-IDREF-list="Z"/>
          <W i:Restricted-NCName-or-IDREF-list="Z epsilon"/>

        </IDS2>;

declare
    %test:assertEquals("employee")
function fni:id-with-is-id-attribute() {
    $fni:data/id('ID21256')/name()
};

declare
    %test:assertEquals("empnr")
function fni:id-with-is-id-type() {
    $fni:data/id('E21256')/name()
};

declare
    %test:assertEquals("Element-with-ID-attribute", "Element-with-ID-attribute", "id", "id")
function fni:fn-id-1() {
    $fni:ids2/id('alpha beta iota kappa')/local-name()
};

declare
    %test:assertEquals("id", "id")
function fni:fn-id-2() {
    $fni:ids2/id('lambda mu')/local-name()
};

declare
    %test:assertEquals("id")
function fni:fn-id-3() {
    $fni:ids2/id('nu')/local-name()
};

declare
    %test:assertEquals("id", "0")
function fni:fn-id-4() {
    $fni:ids2/id('xi')/local-name(), count($fni:ids2/id('ping'))
};

declare
    %test:assertEquals("id", "0")
function fni:fn-id-5() {
    $fni:ids2/id('omicron')/local-name(), count($fni:ids2/id('853'))
};

declare
    %test:assertError("XPTY0004")
function fni:fn-id-context-item-not-node() {
    (1 to 5)[fn:id("argument1")]
};
