/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.xslfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.exist.util.ParametersExtractor;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.xslfo.XSLFOModule.functionSignatures;

/**
 * @author <a href="mailto:craiggoodyer@gmail.com">Craig Goodyer</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class RenderFunction extends BasicFunction {

    @SuppressWarnings("unused")
    private static final Logger logger = LogManager.getLogger(RenderFunction.class);

    private static final FunctionParameterSequenceType FN_PARAM_DOCUMENT = param("document", Type.NODE, "FO document");
    private static final FunctionParameterSequenceType FN_PARAM_MIME_TYPE = param("media-type", Type.STRING, "The Internet Media Type of the desired result");
    private static final FunctionParameterSequenceType FN_PARAM_PARAMETERS = optParam("parameters", Type.NODE, "parameters for the transform");

    private static final String FN_RENDER = "render";
    public final static FunctionSignature[] signatures = functionSignatures(
        FN_RENDER,
        "Renders a given FO document. "
                + "Returns an xs:base64binary of the result. "
                + "Parameters are specified with the structure: "
                + "<parameters><param name=\"param-name1\" value=\"param-value1\"/>"
                + "</parameters>. "
                + "Recognised rendering parameters are: author, title, keywords and dpi. URL's in the FO can be resolved from: http, https, file and exist URI schemes. If you wish to access a resource in the local database then the URI 'exist://localhost/db' refers to the root collection.",
        returnsOpt(Type.BASE64_BINARY, "The result of rendering the FO"),
        arities(
            arity(
                    FN_PARAM_DOCUMENT,
                    FN_PARAM_MIME_TYPE,
                    FN_PARAM_PARAMETERS
            ),
            arity(
                    FN_PARAM_DOCUMENT,
                    FN_PARAM_MIME_TYPE,
                    FN_PARAM_PARAMETERS,
                    optParam("processor-config", Type.NODE, "FOP Processor Configuration file")
            )
        )
    );

    public RenderFunction(final XQueryContext context, final FunctionSignature signature) {
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
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        // gather input XSL-FO document
        // if no input document (empty), return empty result as we need data to
        // process
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item inputNode = args[0].itemAt(0);

        // get media-type
        final String mediaType = args[1].getStringValue();

        // get parameters
        final Properties parameters;
        if (!args[2].isEmpty()) {
            parameters = ParametersExtractor.parseParameters(((NodeValue) args[2].itemAt(0)).getNode());
        } else {
            parameters = new Properties();
        }

        ProcessorAdapter adapter = null;
        try (final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().get()) {
            adapter = ((XSLFOModule) getParentModule()).getProcessorAdapter();

            final NodeValue processorConfig = args.length == 4 ? (NodeValue) args[3].itemAt(0) : null;
            final ContentHandler contentHandler = adapter.getContentHandler(context.getBroker(), processorConfig, parameters, mediaType, baos);

            // process the XSL-FO
            contentHandler.startDocument();
            inputNode.toSAX(context.getBroker(), contentHandler, new Properties());
            contentHandler.endDocument();

            // return the result
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()), this);
        } catch (final IOException | SAXException se) {
            LOG.error(se.getMessage(), se);
            throw new XPathException(this, se.getMessage(), se);
        } finally {
            if (adapter != null) {
                adapter.cleanup();
            }
        }
    }
}