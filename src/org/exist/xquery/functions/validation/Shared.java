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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;

import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  Shared methods for validation functions.
 *
 * @author dizzzz
 */
public class Shared {

    private final static Logger logger = Logger.getLogger(Shared.class);

    public final static String simplereportText = "Returns true() if the " +
            "document is valid and no single problem occured, false() for " +
            "all other situations. Check corresponding report function " +
            "for details.";
    
    public final static String xmlreportText = "Validation report formatted as\n<report>\n" +
            "\t<status>valid</status>\n" + "\t<namespace>...\n" + "\t<time>...\n" +
            "\t<exception>\n" +
            "\t\t<class>...\n" + "\t\t<message>...\n" + "\t\t<stacktrace>...\n" +
            "\t</exception>\n" +
            "\t<message level=\"\" line=\"\" column=\"\" repeat=\"\">...</message>\n" +
            "\t....\n" +
            "\t....\n" +
            "</report>";

    /**
     *  Get input stream for specified resource.
     */
    public static InputStream getInputStream(Sequence s, XQueryContext context) throws XPathException, MalformedURLException, IOException {
        StreamSource streamSource = getStreamSource(s, context);
        return streamSource.getInputStream();
    }

    /**
     *  Get stream source for specified resource, containing InputStream and 
     * location. Used by @see Jaxv.
     */
    public static StreamSource getStreamSource(Sequence s, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        StreamSource streamSource = new StreamSource();

        if (s.getItemType() == Type.JAVA_OBJECT) {
            logger.debug("Streaming Java object");
            Item item = s.itemAt(0);
            Object obj = ((JavaObjectValue) item).getObject();
            if (!(obj instanceof File)) {
                throw new XPathException("Passed java object should be a File");
            }

            File inputFile = (File) obj;
            InputStream is = new FileInputStream(inputFile);
            streamSource.setInputStream(is);
            streamSource.setSystemId(inputFile.toURI().toURL().toString());


        } else if (s.getItemType() == Type.ANY_URI) {
            logger.debug("Streaming xs:anyURI");

            // anyURI provided
            String url = s.getStringValue();

            // Fix URL
            if (url.startsWith("/")) {
                url = "xmldb:exist://" + url;
            }

            InputStream is = new URL(url).openStream();
            streamSource.setInputStream(is);
            streamSource.setSystemId(url);

        } else if (s.getItemType() == Type.ELEMENT || s.getItemType() == Type.DOCUMENT) {
            logger.debug("Streaming element or document node");

            // Node provided
            Serializer serializer=context.getBroker().newSerializer();
            InputStream is = new NodeInputStream(serializer, s.iterate()); // new NodeInputStream()
            streamSource.setInputStream(is);

        } else {
            logger.error("Wrong item type " + Type.getTypeName(s.getItemType()));
            throw new XPathException("wrong item type " + Type.getTypeName(s.getItemType()));
        }

        return streamSource;
    }

    /**
     *  Get input source for specified resource, containing inputStream and 
     * location. Used by @see Jing.
     */
    public static InputSource getInputSource(Sequence s, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        StreamSource streamSource = getStreamSource(s, context);

        InputSource inputSource = new InputSource();
        inputSource.setByteStream(streamSource.getInputStream());
        inputSource.setSystemId(streamSource.getSystemId());

        return inputSource;

    }

    /**
     *  Get URL value of parameter.
     */
    public static String getUrl(Sequence s) throws XPathException {

        if (s.getItemType() != Type.ANY_URI && s.getItemType() != Type.STRING) {
            throw new XPathException("Parameter should be of type xs:anyURI" +
                    " or string");
        }

        String url = s.getStringValue();

        if (url.startsWith("/")) {
            url = "xmldb:exist://" + url;
        }

        return url;
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
