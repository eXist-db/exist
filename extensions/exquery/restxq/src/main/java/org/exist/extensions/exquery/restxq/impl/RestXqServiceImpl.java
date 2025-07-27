/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.extensions.exquery.xdm.type.impl.BinaryTypedValue;
import org.exist.extensions.exquery.xdm.type.impl.DocumentTypedValue;
import org.exist.extensions.exquery.xdm.type.impl.StringTypedValue;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.BinaryValueManager;
import org.exist.xquery.value.StringValue;
import org.exquery.http.HttpRequest;
import org.exquery.http.HttpResponse;
import org.exquery.restxq.ResourceFunction;
import org.exquery.restxq.ResourceFunctionExecuter;
import org.exquery.restxq.RestXqErrorCodes;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.RestXqServiceSerializer;
import org.exquery.restxq.impl.AbstractRestXqService;
import org.exquery.xdm.type.SequenceImpl;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class RestXqServiceImpl extends AbstractRestXqService {

    private final static Logger LOG = LogManager.getLogger(RestXqServiceImpl.class);

    private final BrokerPool brokerPool;
    private final BinaryValueManager binaryValueManager;

    public RestXqServiceImpl(final ResourceFunction resourceFunction, final BrokerPool brokerPool) {
        super(resourceFunction);
        this.brokerPool = brokerPool;
        this.binaryValueManager = new BinaryValueManager() {

            final Deque<BinaryValue> binaryValues = new ArrayDeque<>();

            @Override
            public void registerBinaryValueInstance(final BinaryValue binaryValue) {
                binaryValues.push(binaryValue);
            }

            @Override
            public void runCleanupTasks(final Predicate<Object> predicate) {
                while (!binaryValues.isEmpty()) {
                    try {
                        if(predicate.test(binaryValues.peek())) {
                            final BinaryValue binaryValue = binaryValues.pop();
                            binaryValue.close();
                        }
                    } catch (final IOException ioe) {
                        LOG.error("Unable to close binary value: {}", ioe.getMessage(), ioe);
                    }
                }
            }

            @Override
            public String getCacheClass() {
                return (String) getBrokerPool().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
            }
        };
    }

    private BrokerPool getBrokerPool() {
        return brokerPool;
    }

    @Override
    public void service(final HttpRequest request, final HttpResponse response, final ResourceFunctionExecuter resourceFunctionExecuter, final RestXqServiceSerializer restXqServiceSerializer) throws RestXqServiceException {
        super.service(request, response, resourceFunctionExecuter, restXqServiceSerializer);
        binaryValueManager.runCleanupTasks();
    }

    @Override
    protected Sequence extractRequestBody(final HttpRequest request) throws RestXqServiceException {

        //TODO don't use close shield input stream and move parsing of form parameters from HttpServletRequestAdapter into RequestBodyParser
        InputStream is;
        FilterInputStreamCache cache = null;

        try {

            //first, get the content of the request
            is = CloseShieldInputStream.wrap(request.getInputStream());

            //if marking is not supported, we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
            if (!is.markSupported()) {
                cache = FilterInputStreamCacheFactory.getCacheInstance(() -> {
                    final Configuration configuration = getBrokerPool().getConfiguration();
                    return (String) configuration.getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                }, is);

                is = new CachingFilterInputStream(cache);
            }

            is.mark(Integer.MAX_VALUE);
        } catch (final IOException ioe) {
            throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, ioe);
        }

        Sequence result = null;
        try {

            //was there any POST content?
            if (is != null) {
                String contentType = request.getContentType();
                // 1) determine if exists mime database considers this binary data
                if (contentType != null) {
                    //strip off any charset encoding info
                    if (contentType.contains(";")) {
                        contentType = contentType.substring(0, contentType.indexOf(";"));
                    }

                    MimeType mimeType = MimeTable.getInstance().getContentType(contentType);
                    if (mimeType != null && !mimeType.isXMLType()) {

                        //binary data
                        try {

                            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), is, null);
                            if (binaryValue != null) {
                                result = new SequenceImpl<>(new BinaryTypedValue(binaryValue));
                            }
                        } catch (final XPathException xpe) {
                            throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, xpe);
                        }
                    }
                }

                if (result == null) {
                    //2) not binary, try and parse as an XML document
                    final DocumentImpl doc = parseAsXml(is);
                    if (doc != null) {
                        result = new SequenceImpl<>(new DocumentTypedValue(doc));
                    }
                }

                if (result == null) {

                    String encoding = request.getCharacterEncoding();
                    // 3) not a valid XML document, return a string representation of the document
                    if (encoding == null) {
                        encoding = "UTF-8";
                    }

                    try {
                        //reset the stream, as we need to reuse for string parsing
                        is.reset();

                        final StringValue str = parseAsString(is, encoding);
                        if (str != null) {
                            result = new SequenceImpl<>(new StringTypedValue(str));
                        }
                    } catch (final IOException ioe) {
                        throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, ioe);
                    }
                }
            }
        } finally {

            if (cache != null) {
                try {
                    cache.invalidate();
                } catch (final IOException ioe) {
                    LOG.error(ioe.getMessage(), ioe);
                }
            }

            if (is != null) {
                /*
                 * Do NOT close the stream if its a binary value,
                 * because we will need it later for serialization
                 */
                boolean isBinaryType = false;
                if (result != null) {
                    try {
                        final Type type = result.head().getType();
                        isBinaryType = (type == Type.BASE64_BINARY || type == Type.HEX_BINARY);
                    } catch (final IndexOutOfBoundsException ioe) {
                        LOG.warn("Called head on an empty HTTP Request body sequence", ioe);
                    }
                }

                if (!isBinaryType) {
                    try {
                        is.close();
                    } catch (final IOException ioe) {
                        LOG.error(ioe.getMessage(), ioe);
                    }
                }
            }
        }

        if (result != null) {
            return result;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    private DocumentImpl parseAsXml(final InputStream is) {

        DocumentImpl result = null;
        XMLReader reader = null;

        try {
            //try and construct xml document from input stream, we use eXist's in-memory DOM implementation

            //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
            final InputSource src = new InputSource(CloseShieldInputStream.wrap(is));

            reader = getBrokerPool().getParserPool().borrowXMLReader();
            final MemTreeBuilder builder = new MemTreeBuilder();
            builder.startDocument();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(null, builder, true);
            reader.setContentHandler(receiver);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", receiver);
            reader.parse(src);
            builder.endDocument();
            final Document doc = receiver.getDocument();

            result = (DocumentImpl) doc;
        } catch (final SAXException | IOException saxe) {
            //do nothing, we will default to trying to return a string below
        } finally {
            if (reader != null) {
                getBrokerPool().getParserPool().returnXMLReader(reader);
            }
        }

        return result;
    }

    private static StringValue parseAsString(final InputStream is, final String encoding) throws IOException {
        final String s;
        try (final UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().setBufferSize(4096).get()) {
            bos.write(is);
            s = new String(bos.toByteArray(), encoding);
        }
        return new StringValue(s);
    }
}
