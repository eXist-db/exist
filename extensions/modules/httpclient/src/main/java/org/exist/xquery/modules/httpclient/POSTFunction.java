/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.modules.httpclient;

import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.util.io.FastByteArrayOutputStream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.exist.dom.QName;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Performs HTTP Post method
 *
 * @author Adam Retter <adam@exist-db.org>
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @version 1.3
 * @serial 20100228
 */
public class POSTFunction extends BaseHTTPClientFunction {
    protected static final Logger logger = LogManager.getLogger(POSTFunction.class);

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("post", NAMESPACE_URI, PREFIX),
                    "Performs a HTTP POST request." + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>" +
                            " where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.",
                    new SequenceType[]{
                            URI_PARAM,
                            POST_CONTENT_PARAM,
                            PERSIST_PARAM,
                            REQUEST_HEADER_PARAM
                    },
                    XML_BODY_RETURN
            ),

            new FunctionSignature(
                    new QName("post-form", NAMESPACE_URI, PREFIX),
                    "Performs a HTTP POST request for a form." + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>" +
                            " where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.",
                    new SequenceType[]{
                            URI_PARAM,
                            POST_FORM_PARAM,
                            PERSIST_PARAM,
                            REQUEST_HEADER_PARAM
                    },
                    XML_BODY_RETURN
            )
    };


    public POSTFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        Sequence response = null;

        // must be a URL
        if (args[0].isEmpty()) {
            return (Sequence.EMPTY_SEQUENCE);
        }

        //get the url
        final String url = args[0].itemAt(0).getStringValue();

        //get the payload

        final Item payload = args[1].itemAt(0);

        //get the persist state
        final boolean persistState = args[2].effectiveBooleanValue();

        final PostMethod post = new PostMethod(url);

        if (isCalledAs("post")) {
            RequestEntity entity = null;

            if (Type.subTypeOf(payload.getType(), Type.NODE)) {

                //serialize the node to SAX



                try(final FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
                        final OutputStreamWriter osw = new OutputStreamWriter(baos, UTF_8)) {

                    final SAXSerializer sax = new SAXSerializer(osw, new Properties());

                    payload.toSAX(context.getBroker(), sax, new Properties());
                    osw.flush();

                    final byte[] reqPayload = baos.toByteArray();
                    entity = new ByteArrayRequestEntity(reqPayload, "application/xml; charset=utf-8");
                } catch (final Exception e) {
                    throw new XPathException(this, e.getMessage());
                }
            } else {

                try {
                    entity = new StringRequestEntity(payload.getStringValue(), "text/text; charset=utf-8", "UTF-8");
                } catch (final UnsupportedEncodingException uee) {
                    uee.printStackTrace();
                }
            }

            post.setRequestEntity(entity);
        } else if (isCalledAs("post-form")) {
            final Node nPayload = ((NodeValue) payload).getNode();
            final String ns = nPayload.getNamespaceURI();
            if ((nPayload instanceof Element) && ns != null && ns.equals(HTTPClientModule.NAMESPACE_URI) && nPayload.getLocalName().equals("fields")) {
                final Part[] parts = parseFields((Element) nPayload);
                post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));
            } else {
                throw new XPathException(this, "fields must be provided");
            }

        } else {
            return Sequence.EMPTY_SEQUENCE;
        }

        //setup POST Request Headers
        if (!args[3].isEmpty()) {
            setHeaders(post, ((NodeValue) args[3].itemAt(0)).getNode());
        }

        try {

            //execute the request
            response = doRequest(context, post, persistState, null, null);

        } catch (final IOException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        } finally {
            post.releaseConnection();
        }

        return response;
    }


    private Part[] parseFields(final Element fields) throws XPathException {
        final NodeList nlField = fields.getElementsByTagNameNS(HTTPClientModule.NAMESPACE_URI, "field");
        final Part[] parts = new Part[nlField.getLength()];
        for (int i = 0; i < nlField.getLength(); i++) {
            final Element field = (Element) nlField.item(i);

            if (field.hasAttribute("type") && field.getAttribute("type").equals("file")) {
                try {
                    final String url = field.getAttribute("value");
                    if (url.startsWith("xmldb:exist://")) {
                        parts[i] = new FilePart(field.getAttribute("name"), new DBFile(url.substring(13)));
                    } else {
                        parts[i] = new FilePart(field.getAttribute("name"), new File(url));
                    }

                } catch (final FileNotFoundException e) {
                    throw new XPathException(e);
                }

            } else {
                parts[i] = new StringPart(field.getAttribute("name"), field.getAttribute("value"));
            }

        }

        return parts;
    }
}
