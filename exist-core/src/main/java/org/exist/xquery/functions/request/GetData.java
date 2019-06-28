/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.request;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@exist-db.org">Adam retter</a>
 */
public class GetData extends StrictRequestFunction {

    protected static final Logger logger = LogManager.getLogger(GetData.class);

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-data", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
            "Returns the content of a POST request. "
            + "If the HTTP Content-Type header in the request identifies it as a binary document, then xs:base64Binary is returned. "
            + "If its not a binary document, we attempt to parse it as XML and return a document-node(). "
            + "If its not a binary or XML document, any other data type is returned as an xs:string representation or "
            + "an empty sequence if there is no data to be read.",
            null,
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the content of a POST request")
    );

    public GetData(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
            throws XPathException {

        //if the content length is unknown or 0, return
        if (request.getContentLength() <= 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        InputStream isRequest = null;
        Sequence result = Sequence.EMPTY_SEQUENCE;
        try {

            isRequest = request.getInputStream();

            //was there any POST content?
            /**
             * There is a bug in HttpInput.available() in Jetty 7.2.2.v20101205
             * This has been filed as Bug 333415 -
             * https://bugs.eclipse.org/bugs/show_bug.cgi?id=333415 It is
             * expected to be fixed in the Jetty 7.3.0 release
             */
            //TODO reinstate call to .available() when Jetty 7.3.0 is released, use of .getContentLength() is not reliable because of http mechanics
            //if(is != null && is.available() > 0) {
            if (isRequest != null && request.getContentLength() > 0) {

                // 1) determine if exists mime database considers this binary data
                String contentType = request.getContentType();
                if (contentType != null) {
                    //strip off any charset encoding info
                    if (contentType.indexOf(";") > -1) {
                        contentType = contentType.substring(0, contentType.indexOf(";"));
                    }

                    final MimeType mimeType = MimeTable.getInstance().getContentType(contentType);
                    if (mimeType != null && !mimeType.isXMLType()) {

                        //binary data
                        result = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), isRequest);
                    }
                }

                if (result == Sequence.EMPTY_SEQUENCE) {
                    //2) not binary, try and parse as an XML documemnt, otherwise 3) return a string representation

                    //parsing will consume the stream so we must cache!
                    InputStream is = null;
                    FilterInputStreamCache cache = null;
                    try {
                        //we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
                        cache = FilterInputStreamCacheFactory.getCacheInstance(new FilterInputStreamCacheConfiguration() {

                            @Override
                            public String getCacheClass() {
                                return (String) context.getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                            }
                        }, isRequest);
                        is = new CachingFilterInputStream(cache);

                        //mark the start of the stream
                        is.mark(Integer.MAX_VALUE);

                        //2) try and  parse as XML
                        result = parseAsXml(is);

                        if (result == Sequence.EMPTY_SEQUENCE) {
                            // 3) not a valid XML document, return a string representation of the document
                            String encoding = request.getCharacterEncoding();
                            if (encoding == null) {
                                encoding = "UTF-8";
                            }

                            try {
                                //reset the stream, as we need to reuse for string parsing after the XML parsing happened
                                is.reset();

                                result = parseAsString(is, encoding);
                            } catch (final IOException ioe) {
                                throw new XPathException(this, "An IO exception occurred: " + ioe.getMessage(), ioe);
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
                            try {
                                is.close();
                            } catch (final IOException ioe) {
                                LOG.error(ioe.getMessage(), ioe);
                            }
                        }
                    }
                }

                //NOTE we do not close isRequest, because it may be needed further by the caching input stream wrapper
            }
        } catch (final IOException ioe) {
            throw new XPathException(this, "An IO exception occurred: " + ioe.getMessage(), ioe);
        }

        return result;
    }

    private Sequence parseAsXml(InputStream is) {

        Sequence result = Sequence.EMPTY_SEQUENCE;
        XMLReader reader = null;

        context.pushDocumentContext();
        try {
            //try and construct xml document from input stream, we use eXist's in-memory DOM implementation

            //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
            final InputSource src = new InputSource(new CloseShieldInputStream(is));

            reader = context.getBroker().getBrokerPool().getParserPool().borrowXMLReader();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            reader.setContentHandler(receiver);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, receiver);
            reader.parse(src);
            final Document doc = receiver.getDocument();

            result = (NodeValue) doc;
        } catch (final SAXException saxe) {
            //do nothing, we will default to trying to return a string below
        } catch (final IOException ioe) {
            //do nothing, we will default to trying to return a string below
        } finally {
            context.popDocumentContext();

            if (reader != null) {
                context.getBroker().getBrokerPool().getParserPool().returnXMLReader(reader);
            }
        }

        return result;
    }

    private Sequence parseAsString(InputStream is, String encoding) throws IOException {
        try (final FastByteArrayOutputStream bos = new FastByteArrayOutputStream()) {
            bos.write(is);
            return new StringValue(bos.toString(encoding));
        }
    }
}
