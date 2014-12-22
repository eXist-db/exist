/*
Copyright (c) 2014, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.xquery;

import org.custommonkey.xmlunit.XMLAssert;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exquery.serialization.annotation.MediaTypeAnnotation;
import org.exquery.serialization.annotation.MethodAnnotation;
import org.exquery.serialization.annotation.SerializationAnnotationException;
import org.exquery.serialization.annotation.SerializationAnnotationName;
import org.exquery.xquery.Literal;
import org.exquery.xquery.Type;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Override;
import java.net.URISyntaxException;

/**
 * Tests for RegistryFunctions
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RegistryFunctionsTest {

    @Test
    public void outputMediaType() throws URISyntaxException, TransformerException, IOException, SAXException, SerializationAnnotationException {

        //test setup
        final String internetMediaType = "application/octet-stream";
        final MediaTypeAnnotation mediaType = new MediaTypeAnnotation();
        mediaType.setName(SerializationAnnotationName.mediatype.getQName());
        mediaType.setLiterals(new Literal[] {
            new Literal() {
                @Override
                public Type getType() {
                    return Type.STRING;
                }

                @Override
                public String getValue() {
                    return internetMediaType;
                }
            }
        });
        mediaType.initialise();

        //execute serialize method
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        RegistryFunctions.serializeSerializationAnnotation(builder, mediaType);
        builder.endDocument();

        //assert result
        final String xmlResult = documentToString(builder.getDocument());
        XMLAssert.assertXMLEqual("<media-type xmlns=\"http://www.w3.org/2010/xslt-xquery-serialization\">" + internetMediaType + "</media-type>", xmlResult);
    }

    @Test
    public void outputMethod() throws SerializationAnnotationException, TransformerException, IOException, SAXException {
        //test setup
        final String methodStr = "html5";
        final MethodAnnotation method = new MethodAnnotation();
        method.setName(SerializationAnnotationName.method.getQName());
        method.setLiterals(new Literal[]{
            new Literal() {
                @Override
                public Type getType() {
                    return Type.STRING;
                }

                @Override
                public String getValue() {
                    return methodStr;
                }
            }
        });
        method.initialise();

        //execute serialize method
        final MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();
        RegistryFunctions.serializeSerializationAnnotation(builder, method);
        builder.endDocument();

        //assert result
        final String xmlResult = documentToString(builder.getDocument());
        XMLAssert.assertXMLEqual("<method xmlns=\"http://www.w3.org/2010/xslt-xquery-serialization\">" + methodStr + "</method>", xmlResult);
    }

    private String documentToString(final Document doc) throws TransformerException, IOException {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        try(final Writer sw = new StringWriter()) {
            final Result sr = new StreamResult(sw);
            transformer.transform(new DOMSource(doc), sr);
            return sw.toString();
        }
    }
}
