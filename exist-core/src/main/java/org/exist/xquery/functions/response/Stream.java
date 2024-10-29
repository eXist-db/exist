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
package org.exist.xquery.functions.response;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Stream extends StrictResponseFunction {

    private static final Logger logger = LogManager.getLogger(Stream.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("stream", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Stream can only be used within a servlet context. It directly streams its input to the servlet's output stream. " +
                            "It should thus be the last statement in the XQuery.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence"),
                            new FunctionParameterSequenceType("serialization-options", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization options")},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            );

    public Stream(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence inputNode = args[0];
        final Properties serializeOptions = new Properties();
        final String serOpts = args[1].getStringValue();
        final String[] contents = Option.tokenize(serOpts);
        for (String content : contents) {
            final String[] pair = Option.parseKeyValuePair(content);
            if (pair == null) {
                throw new XPathException(this, "Found invalid serialization option: " + content);
            }
            if (LOG.isDebugEnabled()) {
                logger.debug("Setting serialization property: {} = {}", pair[0], pair[1]);
            }
            serializeOptions.setProperty(pair[0], pair[1]);
        }

        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(response.getClass().getName())) {
            throw new XPathException(this, ErrorCodes.XPDY0002, signature.toString() + " can only be used within the EXistServlet or XQueryServlet");
        }

        final String mediaType = serializeOptions.getProperty("media-type", "application/xml");
        final String encoding = serializeOptions.getProperty("encoding", UTF_8.name());
        if (mediaType != null) {
            response.setContentType(mediaType + "; charset=" + encoding);
        }

        try {
            final BrokerPool db = BrokerPool.getInstance();
            try (final DBBroker broker = db.getBroker();
                    final PrintWriter output = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), encoding))) {

                final Serializer serializer = broker.borrowSerializer();
                final SerializerPool serializerPool = SerializerPool.getInstance();
                final SAXSerializer sax = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
                try {
                    sax.setOutput(output, serializeOptions);

                    serializer.setProperties(serializeOptions);
                    serializer.setSAXHandlers(sax, sax);
                    serializer.toSAX(inputNode, 1, inputNode.getItemCount(), false, false, 0, 0);

                } catch (final SAXException e) {
                    e.printStackTrace();
                    throw new IOException(e);
                } finally {
                    serializerPool.returnObject(sax);
                    broker.returnSerializer(serializer);
                }

                output.flush();
            }

            //commit the response
            response.flushBuffer();

        } catch (final IOException e) {
            throw new XPathException(this, "IO exception while streaming node: " + e.getMessage(), e);
        } catch (final EXistException e) {
            throw new XPathException(this, "Exception while streaming node: " + e.getMessage(), e);
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}