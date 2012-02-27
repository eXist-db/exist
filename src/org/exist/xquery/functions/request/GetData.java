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

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.http.servlets.RequestWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/**
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author Adam retter <adam@exist-db.org>
 */
public class GetData extends BasicFunction {

    protected static final Logger logger = Logger.getLogger(GetData.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-data", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
        "Returns the content of a POST request. " +
        "If the HTTP Content-Type header in the request identifies it as a binary document, then xs:base64Binary is returned. " +
        "If its not a binary document, we attempt to parse it as XML and return a document-node(). " +
        "If its not a binary or XML document, any other data type is returned as an xs:string representation or " +
        "an empty sequence if there is no data to be read.",
        null,
        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the content of a POST request")
    );
	
    public GetData(XQueryContext context) {
        super(context, signature);
    }
	
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)throws XPathException {
		
        RequestModule myModule = (RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

        // request object is read from global variable $request
        Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);

        if(var == null || var.getValue() == null) {
            throw new XPathException(this, "No request object found in the current XQuery context.");
        }

        if(var.getValue().getItemType() != Type.JAVA_OBJECT) {
            throw new XPathException(this, "Variable $request is not bound to an Java object.");
        }

        JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);

        if(!(value.getObject() instanceof RequestWrapper)) {
            throw new XPathException(this, "Variable $request is not bound to a Request object.");
        }
        RequestWrapper request = (RequestWrapper)value.getObject();

        //if the content length is unknown or 0, return
        if(request.getContentLength() == -1 || request.getContentLength() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        //first, get the content of the request
        InputStream is = null;
        FilterInputStreamCache cache = null;
        try {
            //we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
            cache = FilterInputStreamCacheFactory.getCacheInstance();
            is = new CachingFilterInputStream(cache, request.getInputStream());
            is.mark(Integer.MAX_VALUE);
        } catch(IOException ioe) {
            throw new XPathException(this, "An IO exception occurred: " + ioe.getMessage(), ioe);
        }

        Sequence result = Sequence.EMPTY_SEQUENCE;
        try {

            //was there any POST content?


            /**
             * There is a bug in HttpInput.available() in Jetty 7.2.2.v20101205
             * This has been filed as Bug 333415 - https://bugs.eclipse.org/bugs/show_bug.cgi?id=333415
             * It is expected to be fixed in the Jetty 7.3.0 release
             */

            //TODO reinstate call to .available() when Jetty 7.3.0 is released, use of .getContentLength() is not reliable because of http mechanics
            //if(is != null && is.available() > 0) {
            if(is != null && request.getContentLength() > 0) {

                // 1) determine if exists mime database considers this binary data
                String contentType = request.getContentType();
                if(contentType != null) {
                    //strip off any charset encoding info
                    if(contentType.indexOf(";") > -1) {
                        contentType = contentType.substring(0, contentType.indexOf(";"));
                    }

                    MimeType mimeType = MimeTable.getInstance().getContentType(contentType);
                    if(mimeType != null && !mimeType.isXMLType()) {

                        //binary data

                        result = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), is);
                    }
                }

                if(result == Sequence.EMPTY_SEQUENCE) {
                    //2) not binary, try and parse as an XML documemnt
                    result = parseAsXml(is);
                }

                if(result == Sequence.EMPTY_SEQUENCE) {

                    // 3) not a valid XML document, return a string representation of the document
                    String encoding = request.getCharacterEncoding();
                    if(encoding == null) {
                        encoding = "UTF-8";
                    }

                    try {
                        //reset the stream, as we need to reuse for string parsing
                        is.reset();

                        result = parseAsString(is, encoding);
                    } catch(IOException ioe) {
                        throw new XPathException(this, "An IO exception occurred: " + ioe.getMessage(), ioe);
                    }
                }
            }
        /* } catch(IOException ioe) {
            LOG.error(ioe.getMessage(), ioe); */
        } finally {

            if(cache != null) {
                try {
                    cache.invalidate();
                } catch(IOException ioe) {
                    LOG.error(ioe.getMessage(), ioe);
                }
            }

            //dont close the stream if its a binary value, because we will need it later for serialization
            if(is != null && !(result instanceof BinaryValue)) {
                try {
                    is.close();
                } catch(IOException ioe) {
                    LOG.error(ioe.getMessage(), ioe);
                }
            }
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
            InputSource src = new InputSource(new CloseShieldInputStream(is));

            reader = context.getBroker().getBrokerPool().getParserPool().borrowXMLReader();
            MemTreeBuilder builder = context.getDocumentBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            reader.setContentHandler(receiver);
            reader.parse(src);
            Document doc = receiver.getDocument();

            result = (NodeValue)doc;
        } catch(SAXException saxe) {
            //do nothing, we will default to trying to return a string below
        } catch(IOException ioe) {
            //do nothing, we will default to trying to return a string below
        } finally {
            context.popDocumentContext();

            if(reader != null) {
                context.getBroker().getBrokerPool().getParserPool().returnXMLReader(reader);
            }
        }

        return result;
    }

    private Sequence parseAsString(InputStream is, String encoding) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read = -1;
        while ((read = is.read(buf)) > -1) {
            bos.write(buf, 0, read);
        }
        String s = new String(bos.toByteArray(), encoding);
        return new StringValue(s);
    }
}