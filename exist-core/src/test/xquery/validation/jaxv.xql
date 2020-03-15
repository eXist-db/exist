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

module namespace val ="http://exist-db.org/xquery/test/validation";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $val:XML := <root>
                                <value1>20</value1>
                                <value2>30</value2>
                            </root>;

declare variable $val:XSD11_1 := <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                                        <xs:element name="root">
                                          <xs:complexType>
                                            <xs:sequence>
                                              <xs:element ref="value1"/>
                                              <xs:element ref="value2"/>
                                            </xs:sequence>
                                            <!-- XSD11 assertion -->
                                           <xs:assert test="value2 gt value1"></xs:assert>
                                          </xs:complexType>
                                        </xs:element>
                                        <xs:element name="value1" type="xs:integer"/>
                                        <xs:element name="value2" type="xs:integer"/>
                                      </xs:schema>;

declare variable $val:XSD11_2 := <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                                        <xs:element name="root">
                                          <xs:complexType>
                                            <xs:sequence>
                                              <xs:element ref="value1"/>
                                              <xs:element ref="value2"/>
                                            </xs:sequence>
                                            <!-- XSD11 assertion -->
                                           <xs:assert test="value2 lt value1"></xs:assert>
                                          </xs:complexType>
                                        </xs:element>
                                        <xs:element name="value1" type="xs:integer"/>
                                        <xs:element name="value2" type="xs:integer"/>
                                      </xs:schema>;

(: Verify that for JAXV it is required to specify the XSD version :)
declare
    %test:assertEquals("s4s-elt-invalid-content.1: The content of '#AnonType_root' is invalid. Element 'assert' is invalid, misplaced, or occurs too often.")
function val:xsd11_no_xsd11_namespace() {
    data(validation:jaxv-report($val:XML ,$val:XSD11_1)//message)
};

(: Good weather scenario : XML is valid:)
declare
    %test:assertEquals("valid")
function val:xsd11_valid() {
    data(validation:jaxv-report($val:XML, $val:XSD11_1, "http://www.w3.org/XML/XMLSchema/v1.1")//status)
};

(: Good weather scenario : XML is invalid:)
declare
    %test:assertEquals("cvc-assertion: Assertion evaluation ('value2 lt value1') for element 'root' on schema type '#AnonType_root' did not succeed. ")
function val:xsd11_invalid() {
    data(validation:jaxv-report($val:XML, $val:XSD11_2, "http://www.w3.org/XML/XMLSchema/v1.1")//message)
};

