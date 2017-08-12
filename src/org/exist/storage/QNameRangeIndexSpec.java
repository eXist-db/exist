/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.storage;

import java.util.Map;

import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;

public class QNameRangeIndexSpec extends RangeIndexSpec {

	private QName qname;
	
	public QNameRangeIndexSpec(Map<String, String> namespaces, String name, String typeStr) 
	throws DatabaseConfigurationException {
		try {
            this.type = getSuperType(Type.getType(typeStr));
        } catch (final XPathException e) {
            throw new DatabaseConfigurationException("Unknown type: " + typeStr);
        }
        
        boolean isAttribute = false;
        if (name.startsWith("@")) {
            isAttribute = true;
            name = name.substring(1);
        }

        try {
            final String prefix = QName.extractPrefix(name);
            final String localName = QName.extractLocalName(name);
            String namespaceURI = "";
            if (prefix != null) {
                namespaceURI = namespaces.get(prefix);
                if (namespaceURI == null) {
                    throw new DatabaseConfigurationException("No namespace defined for prefix: " + prefix +
                            " in index definition");
                }
            }

            if (isAttribute) {
                qname = new QName(localName, namespaceURI, prefix, ElementValue.ATTRIBUTE);
            } else {
                qname = new QName(localName, namespaceURI, prefix);
            }
        } catch(final QName.IllegalQNameException e) {
            throw new DatabaseConfigurationException("Invalid qname", e);
        }
	}

	public QName getQName() {
		return qname;
	}
}
