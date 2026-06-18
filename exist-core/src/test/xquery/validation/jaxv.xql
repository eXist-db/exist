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

(:~
 : Catalog tests for jaxv()'s new (instance, grammars, language, catalogs)
 : overload. $val:CATALOG_MAIN_XSD imports "urn:jaxv-test:imported" with the
 : URN itself (not a resolvable file path) as schemaLocation -- a common XML
 : Catalogs convention. There's no relative path for Xerces to fall back on,
 : so the import can only resolve through the stored catalog's <uri> entry
 : that maps that URN to imported.xsd. This proves the catalog argument is
 : actually doing the resolution, not just being accepted and ignored.
 :)
declare variable $val:CATALOG_COLLECTION_NAME := "validation-jaxv-catalog-test";
declare variable $val:CATALOG_COLLECTION := "/db/" || $val:CATALOG_COLLECTION_NAME;

declare variable $val:CATALOG_IMPORTED_XSD :=
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
               targetNamespace="urn:jaxv-test:imported" elementFormDefault="qualified">
        <xs:simpleType name="ratingType">
            <xs:restriction base="xs:integer">
                <xs:minInclusive value="1"/>
                <xs:maxInclusive value="5"/>
            </xs:restriction>
        </xs:simpleType>
    </xs:schema>;

declare variable $val:CATALOG_DOC :=
    <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
        <uri name="urn:jaxv-test:imported" uri="imported.xsd"/>
    </catalog>;

declare variable $val:CATALOG_MAIN_XSD :=
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
               xmlns:imp="urn:jaxv-test:imported"
               targetNamespace="urn:jaxv-test:main" elementFormDefault="qualified">
        <xs:import namespace="urn:jaxv-test:imported" schemaLocation="urn:jaxv-test:imported"/>
        <xs:element name="review">
            <xs:complexType>
                <xs:sequence>
                    <xs:element name="rating" type="imp:ratingType"/>
                </xs:sequence>
            </xs:complexType>
        </xs:element>
    </xs:schema>;

declare variable $val:CATALOG_VALID_XML :=
    <review xmlns="urn:jaxv-test:main"><rating>4</rating></review>;

declare variable $val:CATALOG_INVALID_XML :=
    <review xmlns="urn:jaxv-test:main"><rating>9</rating></review>;

declare
    %test:setUp
function val:catalog_setup() {
    xmldb:create-collection("/db", $val:CATALOG_COLLECTION_NAME),
    xmldb:store($val:CATALOG_COLLECTION, "catalog.xml", $val:CATALOG_DOC),
    xmldb:store($val:CATALOG_COLLECTION, "imported.xsd", $val:CATALOG_IMPORTED_XSD)
};

declare
    %test:tearDown
function val:catalog_cleanup() {
    xmldb:remove($val:CATALOG_COLLECTION)
};

(: Without a catalog, the import can't resolve at all (no schemaLocation hint) --
   schema compilation itself fails, reported as an exception inside the report
   rather than an XQuery-level error. Confirms the catalog argument isn't a no-op. :)
declare
    %test:assertEquals("invalid")
function val:catalog_without_catalog_fails() {
    data(validation:jaxv-report($val:CATALOG_VALID_XML, $val:CATALOG_MAIN_XSD, "http://www.w3.org/2001/XMLSchema")//status)
};

declare
    %test:assertEquals("valid")
function val:catalog_explicit_valid() {
    data(validation:jaxv-report($val:CATALOG_VALID_XML, $val:CATALOG_MAIN_XSD,
        "http://www.w3.org/2001/XMLSchema", doc($val:CATALOG_COLLECTION || "/catalog.xml"))//status)
};

declare
    %test:assertEquals("invalid")
function val:catalog_explicit_invalid() {
    data(validation:jaxv-report($val:CATALOG_INVALID_XML, $val:CATALOG_MAIN_XSD,
        "http://www.w3.org/2001/XMLSchema", doc($val:CATALOG_COLLECTION || "/catalog.xml"))//status)
};

