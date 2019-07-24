/*
 *  eXist Apache FOP Transformation Extension
 *  Copyright (C) 2007 Craig Goodyer at the University of the West of England
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.xslfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.exist.dom.QName;
import org.exist.util.ParametersExtractor;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:craiggoodyer@gmail.com">Craig Goodyer</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class RenderFunction extends BasicFunction {

    @SuppressWarnings("unused")
    private static final Logger logger = LogManager.getLogger(RenderFunction.class);
    public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            new QName("render", XSLFOModule.NAMESPACE_URI, XSLFOModule.PREFIX),
            "Renders a given FO document. "
            + "Returns an xs:base64binary of the result. "
            + "Parameters are specified with the structure: "
            + "<parameters><param name=\"param-name1\" value=\"param-value1\"/>"
            + "</parameters>. "
            + "Recognised rendering parameters are: author, title, keywords and dpi. URL's in the FO can be resolved from: http, https, file and exist URI schemes. If you wish to access a resource in the local database then the URI 'exist://localhost/db' refers to the root collection.",
            new SequenceType[]{
                new FunctionParameterSequenceType("document", Type.NODE, Cardinality.EXACTLY_ONE, "FO document"),
                new FunctionParameterSequenceType("mime-type", Type.STRING, Cardinality.EXACTLY_ONE, ""),
                new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "parameters for the transform")
            },
            new FunctionParameterSequenceType("result", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "result")
        ),

        new FunctionSignature(
            new QName("render", XSLFOModule.NAMESPACE_URI, XSLFOModule.PREFIX),
            "Renders a given FO document. "
            + "Returns an xs:base64binary of the result. "
            + "Parameters are specified with the structure: "
            + "<parameters><param name=\"param-name1\" value=\"param-value1\"/>"
            + "</parameters>. "
            + "Recognised rendering parameters are: author, title, keywords and dpi. URL's in the FO can be resolved from: http, https, file and exist URI schemes. If you wish to access a resource in the local database then the URI 'exist://localhost/db' refers to the root collection.",
            new SequenceType[]{
                new FunctionParameterSequenceType("document", Type.NODE, Cardinality.EXACTLY_ONE, "FO document"),
                new FunctionParameterSequenceType("mime-type", Type.STRING, Cardinality.EXACTLY_ONE, ""),
                new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "parameters for the transform"),
                new FunctionParameterSequenceType("config-file", Type.NODE, Cardinality.ZERO_OR_ONE, "FOP Processor Configuration file")
            },
            new FunctionParameterSequenceType("result", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "result")
        )
    };

    public RenderFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /*
     * Actual implementation of the rendering process. When a function in this
     * module is called, this method is executed with the given inputs. @param
     * Sequence[] args (XSL-FO, mime-type, parameters) @param Sequence
     * contextSequence (default sequence)
     *
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     *      org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // gather input XSL-FO document
        // if no input document (empty), return empty result as we need data to
        // process
        if(args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        Item inputNode = args[0].itemAt(0);

        // get mime-type
        String mimeType = args[1].getStringValue();

        // get parameters
        Properties parameters = new Properties();
        if(!args[2].isEmpty()) {
            parameters = ParametersExtractor.parseParameters(((NodeValue) args[2].itemAt(0)).getNode());
        }

        ProcessorAdapter adapter = null;
        try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
            adapter = ((XSLFOModule)getParentModule()).getProcessorAdapter();

            NodeValue configFile = args.length == 4 ? (NodeValue)args[3].itemAt(0) : null;
            ContentHandler contentHandler = adapter.getContentHandler(context.getBroker(), configFile, parameters, mimeType, baos);

            // process the XSL-FO
            contentHandler.startDocument();
            inputNode.toSAX(context.getBroker(), contentHandler, new Properties());
            contentHandler.endDocument();

            // return the result
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()));
        } catch(final IOException | SAXException se) {
            throw new XPathException(this, se.getMessage(), se);
        } finally {
            if(adapter != null) {
                adapter.cleanup();
            }
        }
    }
}