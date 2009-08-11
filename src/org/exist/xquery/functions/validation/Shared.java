/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.validation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;

import org.exist.dom.NodeProxy;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  Shared methods for validation functions.
 *
 * @author dizzzz
 */
public class Shared {

    private final static Logger LOG = Logger.getLogger(Shared.class);
    public final static String simplereportText = "true() if the " +
            "document is valid and no single problem occured, false() for " +
            "all other conditions. For detailed validation information " +
            "use the corresponding -report() function.";
    public final static String xmlreportText = "a validation report.";

    /**
     *  Get input stream for specified resource.
     */
    public static InputStream getInputStream(Item s, XQueryContext context) throws XPathException, MalformedURLException, IOException {
        StreamSource streamSource = getStreamSource(s, context);
        return streamSource.getInputStream();
    }

    /**
     *  Get stream source for specified resource, containing InputStream and 
     * location. Used by @see Jaxv.
     */
    public static StreamSource[] getStreamSource(Sequence s, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        ArrayList<StreamSource> sources = new ArrayList<StreamSource>();

        SequenceIterator i = s.iterate();

        while (i.hasNext()) {
            Item next = i.nextItem();

            StreamSource streamsource = getStreamSource(next, context);
            sources.add(streamsource);
        }

        StreamSource returnSources[] = new StreamSource[sources.size()];
        returnSources = sources.toArray(returnSources);
        return returnSources;
    }

    public static StreamSource getStreamSource(Item item, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        StreamSource streamSource = new StreamSource();
        if (item.getType() == Type.JAVA_OBJECT) {
            LOG.debug("Streaming Java object");

            Object obj = ((JavaObjectValue) item).getObject();
            if (!(obj instanceof File)) {
                throw new XPathException("Passed java object should be a File");
            }

            File inputFile = (File) obj;
            InputStream is = new FileInputStream(inputFile);
            streamSource.setInputStream(is);
            streamSource.setSystemId(inputFile.toURI().toURL().toString());

        } else if (item.getType() == Type.ANY_URI) {
            LOG.debug("Streaming xs:anyURI");

            // anyURI provided
            String url = item.getStringValue();

            // Fix URL
            if (url.startsWith("/")) {
                url = "xmldb:exist://" + url;
            }

            InputStream is = new URL(url).openStream();
            streamSource.setInputStream(is);
            streamSource.setSystemId(url);

        } else if (item.getType() == Type.ELEMENT || item.getType() == Type.DOCUMENT) {
            LOG.debug("Streaming element or document node");

            if (item instanceof NodeProxy) {
                NodeProxy np = (NodeProxy) item;
                String url = "xmldb:exist://" + np.getDocument().getBaseURI();
                LOG.debug("Document detected, adding URL " + url);
                streamSource.setSystemId(url);
            }

            // Node provided
            Serializer serializer = context.getBroker().newSerializer();

            NodeValue node = (NodeValue) item;
            InputStream is = new NodeInputStream(serializer, node); 
            streamSource.setInputStream(is);

        } else if (item.getType() == Type.BASE64_BINARY) {
            LOG.debug("Streaming base64 binary");

            Base64Binary base64 = (Base64Binary) item;
            byte[] data = (byte[]) base64.toJavaObject(byte[].class);
            InputStream is = new ByteArrayInputStream(data);
            streamSource.setInputStream(is);

            if (item instanceof Base64BinaryDocument) {
                Base64BinaryDocument b64doc = (Base64BinaryDocument) item;
                String url = "xmldb:exist://" + b64doc.getUrl();
                LOG.debug("Base64BinaryDocument detected, adding URL " + url);
                streamSource.setSystemId(url);
            }

        } else {
            LOG.error("Wrong item type " + Type.getTypeName(item.getType()));
            throw new XPathException("wrong item type " + Type.getTypeName(item.getType()));
        }

        return streamSource;
    }

    /**
     *  Get input source for specified resource, containing inputStream and 
     * location. Used by @see Jing.
     */
    public static InputSource getInputSource(Item s, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        StreamSource streamSource = getStreamSource(s, context);

        InputSource inputSource = new InputSource();
        inputSource.setByteStream(streamSource.getInputStream());
        inputSource.setSystemId(streamSource.getSystemId());

        return inputSource;

    }

    public static StreamSource getStreamSource(InputSource in) throws XPathException, MalformedURLException, IOException {

        StreamSource streamSource = new StreamSource();
        streamSource.setInputStream(in.getByteStream());
        streamSource.setSystemId(in.getSystemId());

        return streamSource;
    }

    /**
     *  Get URL value of item.
     */
    public static String getUrl(Item item) throws XPathException {

        String url = null;

        if (item.getType() == Type.ANY_URI /*|| item.getType() != Type.STRING */) {
            LOG.debug("Converting anyURI");
            url = item.getStringValue();

        } else if (item.getType() == Type.DOCUMENT || item.getType() == Type.NODE) {

            LOG.debug("Retreiving URL from (document) node");

            if (item instanceof NodeProxy) {
                NodeProxy np = (NodeProxy) item;
                url = np.getDocument().getBaseURI();
                LOG.debug("Document detected, adding URL " + url);
            }

        }

        if(url==null) {
            throw new XPathException("Parameter should be of type xs:anyURI or document.");
        }
        
        if (url.startsWith("/")) {
            url = "xmldb:exist://" + url;
        }

        return url;
    }

    /**
     *  Get URL values of sequence.
     */
    public static String[] getUrls(Sequence s) throws XPathException {

        ArrayList<String> urls = new ArrayList<String>();

        SequenceIterator i = s.iterate();

        while (i.hasNext()) {
            Item next = i.nextItem();

            String url = getUrl(next);

            urls.add(url);
        }

        String returnUrls[] = new String[urls.size()];
        returnUrls = urls.toArray(returnUrls);
        
        return returnUrls;
    }

    /**
     * Create validation report.
     */
    static public NodeImpl writeReport(ValidationReport report, MemTreeBuilder builder) {

        // start root element
        int nodeNr = builder.startElement("", "report", "report", null);

        // validation status: valid or invalid
        builder.startElement("", "status", "status", null);
        if (report.isValid()) {
            builder.characters("valid");
        } else {
            builder.characters("invalid");
        }
        builder.endElement();

        // namespace when available
        if (report.getNamespaceUri() != null) {
            builder.startElement("", "namespace", "namespace", null);
            builder.characters(report.getNamespaceUri());
            builder.endElement();
        }


        // validation duration
        AttributesImpl durationAttribs = new AttributesImpl();
        durationAttribs.addAttribute("", "unit", "unit", "CDATA", "msec");

        builder.startElement("", "duration", "duration", durationAttribs);
        builder.characters("" + report.getValidationDuration());
        builder.endElement();

        // print exceptions if any
        if (report.getThrowable() != null) {
            builder.startElement("", "exception", "exception", null);

            String className = report.getThrowable().getClass().getName();
            if (className != null) {
                builder.startElement("", "class", "class", null);
                builder.characters(className);
                builder.endElement();
            }

            String message = report.getThrowable().getMessage();
            if (message != null) {
                builder.startElement("", "message", "message", null);
                builder.characters(message);
                builder.endElement();
            }

            String stacktrace = report.getStackTrace();
            if (stacktrace != null) {
                builder.startElement("", "stacktrace", "stacktrace", null);
                builder.characters(stacktrace);
                builder.endElement();
            }

            builder.endElement();
        }

        // reusable attributes
        AttributesImpl attribs = new AttributesImpl();

        // iterate validation report items, write message
        List cr = report.getValidationReportItemList();
        for (Iterator iter = cr.iterator(); iter.hasNext();) {
            ValidationReportItem vri = (ValidationReportItem) iter.next();

            // construct attributes
            attribs.addAttribute("", "level", "level", "CDATA", vri.getTypeText());
            attribs.addAttribute("", "line", "line", "CDATA", Integer.toString(vri.getLineNumber()));
            attribs.addAttribute("", "column", "column", "CDATA", Integer.toString(vri.getColumnNumber()));

            if (vri.getRepeat() > 1) {
                attribs.addAttribute("", "repeat", "repeat", "CDATA", Integer.toString(vri.getRepeat()));
            }

            // write message
            builder.startElement("", "message", "message", attribs);
            builder.characters(vri.getMessage());
            builder.endElement();

            // Reuse attributes
            attribs.clear();
        }

        // finish root element
        builder.endElement();

        // return result
        return ((DocumentImpl) builder.getDocument()).getNode(nodeNr);

    }
}
