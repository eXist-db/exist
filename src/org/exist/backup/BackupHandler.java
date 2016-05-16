/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2016 The eXist Project
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
 */
package org.exist.backup;

import org.exist.Resource;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.util.serializer.SAXSerializer;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface BackupHandler {

    void backup(Resource resource, XMLStreamWriter writer) throws IOException;

    void backup(Collection colection, AttributesImpl attrs);
    void backup(Collection colection, SAXSerializer serializer) throws SAXException;

    void backup(DocumentImpl document, AttributesImpl attrs);
    void backup(DocumentImpl document, SAXSerializer serializer) throws SAXException;
}
