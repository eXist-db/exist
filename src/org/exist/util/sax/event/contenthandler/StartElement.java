/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
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
package org.exist.util.sax.event.contenthandler;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class StartElement extends Element {

    public final Attributes attributes;

    public StartElement(final String namespaceURI, final String localName, final String qname, final Attributes attributes) {
        super(namespaceURI, localName, qname);
        this.attributes = new AttributesImpl(attributes); //make a copy as Xerces reuses the object
    }

    @Override
    public void apply(final ContentHandler handler) throws SAXException {
        handler.startElement(namespaceURI, localName, qname, attributes);
    }
}
