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
 *  $Id: ValidationModule.java 9184 2009-06-23 18:02:14Z dizzzz $
 */
package org.exist.xquery.functions.validation;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;

import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author wessels
 */
public class Shared {

    private final static Logger LOG = Logger.getLogger(Shared.class);

    public static InputStream getInputStream(Sequence s, XQueryContext context) throws XPathException, MalformedURLException, IOException {

        InputStream is = null;

        if (s.getItemType() == Type.ANY_URI) {
            // anyURI provided
            String url = s.getStringValue();

            // Fix URL
            if (url.startsWith("/")) {
                url = "xmldb:exist://" + url;
            }

            is = new URL(url).openStream();

        } else if (s.getItemType() == Type.ELEMENT || s.getItemType() == Type.DOCUMENT) {
            // Node provided
            is = new NodeInputStream(context, s.iterate()); // new NodeInputStream()

        } else {
            LOG.error("Wrong item type " + Type.getTypeName(s.getItemType()));
            throw new XPathException("wrong item type " + Type.getTypeName(s.getItemType()));
        }

        return is;
    }

    public static String getUrl(Sequence s) throws XPathException {
        String url = s.getStringValue();

        if (url.startsWith("/")) {
            url = "xmldb:exist://" + url;
        }

        return url;
    }

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
        builder.startElement("", "time", "time", null);
        builder.characters("" + report.getValidationDuration());
        builder.endElement();

        // print exceptions if any
        if (report.getThrowable() != null) {
            builder.startElement("", "exception", "exception", null);
            builder.characters("" + report.getThrowable().getMessage());
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
