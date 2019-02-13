/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.debugger;

import java.io.IOException;
import java.io.StringReader;

import org.exist.Namespaces;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.XMLReaderPool;
import org.exist.xquery.XQueryContext;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Utils {

    public static NodeImpl nodeFromString(XQueryContext context, String source) throws IOException {
        SAXAdapter adapter = new SAXAdapter(context);

        final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getParserPool();
        XMLReader xr = null;
        try {
            try {
                xr = parserPool.borrowXMLReader();
                xr.setContentHandler(adapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);

            } catch (Exception e) {
                throw new IOException(e);
            }

            try {
                InputSource src = new InputSource(new StringReader(source));
                xr.parse(src);

                return (NodeImpl) adapter.getDocument();
            } catch (SAXException e) {
                throw new IOException(e);
            }
        } finally {
            if (xr != null) {
                parserPool.returnXMLReader(xr);
            }
        }
    }
}
