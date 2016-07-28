/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.util.serializer;

import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.util.XMLString;

public class SAXToReceiver implements ContentHandler, LexicalHandler {

    private Receiver receiver;
    private boolean inCDATASection = false;
    private boolean suppressWhitespace = true;

    public SAXToReceiver(Receiver receiver) {
        this(receiver, false);
    }

    public SAXToReceiver(Receiver receiver, boolean suppressWhitespace) {
        this.receiver = receiver;
        this.suppressWhitespace = suppressWhitespace;
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
        receiver.startDocument();
    }

    public void endDocument() throws SAXException {
        receiver.endDocument();
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        receiver.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        receiver.endPrefixMapping(prefix);
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        String prefix = null;
        int p = qName.indexOf(':');
        if (p > -1)
            {prefix = qName.substring(0, p - 1);}
        final AttrList attrs = new AttrList();
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getQName(i).startsWith("xmlns"))
                {continue;}
            String attrPrefix = null;
            p = atts.getQName(i).indexOf(':');
            if (p > -1)
                {attrPrefix = atts.getQName(i).substring(0, p - 1);}
            int type = AttrImpl.CDATA;
            final String atype = atts.getType(i);
            if ("ID".equals(atype))
                {type = AttrImpl.ID;}
            else if ("IDREF".equals(atype))
                {type = AttrImpl.IDREF;}
            else if ("IDREFS".equals(atype))
                {type = AttrImpl.IDREFS;}
            attrs.addAttribute(new QName(atts.getLocalName(i), atts.getURI(i), attrPrefix), atts.getValue(i), type);
        }
        receiver.startElement(new QName(localName, uri, prefix), attrs);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        String prefix = null;
        final int p = qName.indexOf(':');
        if (p > -1)
            {prefix = qName.substring(0, p - 1);}
        receiver.endElement(new QName(localName, uri, prefix));
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inCDATASection)
            {receiver.cdataSection(ch, start, length);}
        else
            {receiver.characters(new XMLString(ch, start, length));}
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (!suppressWhitespace) {
            receiver.characters(new XMLString(ch, start, length));
        }
    }

    public void processingInstruction(String target, String data) throws SAXException {
        receiver.processingInstruction(target, data);
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void endDTD() throws SAXException {
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startCDATA() throws SAXException {
        inCDATASection = true;
    }

    public void endCDATA() throws SAXException {
        inCDATASection = false;
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        receiver.comment(ch, start, length);
    }
}
