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
 : validation:jaxp() validates by dynamically discovering the grammar from
 : the instance document's own xsi:(no)NamespaceSchemaLocation hint, unlike
 : validation:jaxv() (see jaxv.xql) which takes the grammar as an explicit
 : argument. That dynamic-discovery path needs a real stored document (for a
 : real base URI to resolve the relative schemaLocation hint against), so
 : unlike jaxv.xql this module stores fixtures rather than using in-memory
 : node constructors.
 :)
module namespace jaxp ="http://exist-db.org/xquery/test/validation/jaxp";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $jaxp:COLLECTION_NAME := "validation-jaxp-test";
declare variable $jaxp:COLLECTION := "/db/" || $jaxp:COLLECTION_NAME;

(: No-namespace XSD 1.1 schema -- xs:assert does not exist in XSD 1.0, so a
   processor that silently falls back to 1.0 grammar parsing fails to load
   this schema at all, rather than just failing the assertion. :)
declare variable $jaxp:XSD11 :=
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
               xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1">
        <xs:element name="root">
            <xs:complexType>
                <xs:sequence>
                    <xs:element name="value1" type="xs:integer"/>
                    <xs:element name="value2" type="xs:integer"/>
                </xs:sequence>
                <xs:assert test="value2 gt value1"/>
            </xs:complexType>
        </xs:element>
    </xs:schema>;

declare variable $jaxp:VALID_XML :=
    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="schema.xsd">
        <value1>20</value1>
        <value2>30</value2>
    </root>;

declare variable $jaxp:INVALID_XML :=
    <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="schema.xsd">
        <value1>30</value1>
        <value2>20</value2>
    </root>;

declare
    %test:setUp
function jaxp:setup() {
    xmldb:create-collection("/db", $jaxp:COLLECTION_NAME),
    xmldb:store($jaxp:COLLECTION, "schema.xsd", $jaxp:XSD11),
    xmldb:store($jaxp:COLLECTION, "valid.xml", $jaxp:VALID_XML),
    xmldb:store($jaxp:COLLECTION, "invalid.xml", $jaxp:INVALID_XML)
};

declare
    %test:tearDown
function jaxp:cleanup() {
    xmldb:remove($jaxp:COLLECTION)
};

(: validation:jaxp() must dynamically discover and load an XSD 1.1 schema
   via the instance's own schemaLocation hint, the same as validation:jaxv()
   already does when given the v1.1 schema-language URI explicitly. :)
declare
    %test:assertEquals("valid")
function jaxp:xsd11_valid() {
    data(validation:jaxp-report(doc($jaxp:COLLECTION || "/valid.xml"), false())//status)
};

declare
    %test:assertEquals("cvc-assertion: Assertion evaluation ('value2 gt value1') for element 'root' on schema type '#AnonType_root' did not succeed. ")
function jaxp:xsd11_invalid() {
    data(validation:jaxp-report(doc($jaxp:COLLECTION || "/invalid.xml"), false())//message)
};
