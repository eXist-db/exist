xquery version "1.0" encoding "UTF-8";

declare namespace c="http://www.w3.org/ns/xproc-step";

let $v := <doc>
	<title>Title</title>
	<p>Some paragraph.</p>
      </doc>
let $schema := <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
		 elementFormDefault="qualified">
	<xs:element name="doc">
	  <xs:complexType>
	    <xs:sequence>
	      <xs:element minOccurs="0" ref="title"/>
	      <xs:element minOccurs="0" maxOccurs="unbounded" ref="p"/>
	    </xs:sequence>
	  </xs:complexType>
	</xs:element>
	<xs:element name="title" type="xs:string"/>
	<xs:element name="p" type="xs:string"/>
      </xs:schema>
return

	validation:jing($v,$schema)