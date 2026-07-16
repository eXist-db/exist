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
 : Catalog resolution for xsl:import/xsl:include in transform:transform() and fn:transform().
 :
 : @see https://github.com/eXist-db/exist/issues/350
 : @see https://github.com/eXist-db/exist/issues/5051
 : @see https://github.com/eXist-db/exist/issues/5052
 : @see https://github.com/eXist-db/exist/issues/5682
 :)
module namespace tc="http://exist-db.org/xquery/test/transform/catalog";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
 : A non-routable absolute URI (TEST-NET-3, RFC 5737) -- it cannot resolve via the database, an
 : xmldb:/EXpath-registered location, or a live network fetch, only via the system catalog entry
 : for it in org/exist/validation/catalog.xml.
 :)
declare variable $tc:IMPORT_URI := "http://203.0.113.1/transform-catalog-test-lib.xsl";

(:~
 : Imports $tc:IMPORT_URI and applies its lib:greet() template against the (otherwise unused)
 : context document, shared between both transform functions below so the only difference
 : between the two tests is which function consumes the stylesheet.
 :)
declare variable $tc:STYLESHEET :=
    <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:lib="urn:exist-db:test:transform-catalog" version="3.0">
        <xsl:import href="{$tc:IMPORT_URI}"/>
        <xsl:template match="/">
            <xsl:sequence select="lib:greet()"/>
        </xsl:template>
    </xsl:stylesheet>;

(:~
 : transform:transform() (legacy) must resolve a catalog-redirected xsl:import the same way the
 : Xerces/JAXP validation pipeline already does, via XsltURIResolverHelper's resolver chain.
 :)
declare
    %test:assertEquals("<out>hello</out>")
function tc:legacy-transform-resolves-import-via-catalog() {
    transform:transform(<in>bonjourno</in>, $tc:STYLESHEET, ())
};

(:~
 : fn:transform() should resolve a catalog-redirected xsl:import via its compile-time URIResolver.
 :
 : @see https://github.com/eXist-db/exist/issues/5052
 :)
declare
    %test:pending("https://github.com/eXist-db/exist/issues/5052")
    %test:assertEquals("<out>hello</out>")
function tc:fn-transform-resolves-import-via-catalog() {
    fn:transform(map {
        "stylesheet-node": $tc:STYLESHEET,
        "source-node": <in>bonjourno</in>
    })?output
};

(:~
 : fn:transform() should resolve a catalog-redirected document() call made at runtime, via
 : SaxonConfiguration's Configuration-level ResourceResolver.
 :
 : @see https://github.com/eXist-db/exist/issues/5052
 :)
declare
    %test:pending("https://github.com/eXist-db/exist/issues/5052")
    %test:assertEquals("hello")
function tc:fn-transform-resolves-document-call-via-catalog() {
    fn:transform(map {
        "stylesheet-node":
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                <xsl:template match="/">
                    <xsl:value-of select="document('{$tc:IMPORT_URI}')/xsl:stylesheet/xsl:function/*:out/text()"/>
                </xsl:template>
            </xsl:stylesheet>,
        "source-node": <in>bonjourno</in>
    })?output
};
