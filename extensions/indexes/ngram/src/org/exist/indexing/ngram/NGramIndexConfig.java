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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing.ngram;

import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.exist.storage.ElementValue;

import java.util.Map;

/**
 */
public class NGramIndexConfig {

    private QName qname;

    public NGramIndexConfig(Map namespaces, String name) throws DatabaseConfigurationException {
        boolean isAttribute = false;
        if (name.startsWith("@")) {
            isAttribute = true;
            name = name.substring(1);
        }
        String prefix = QName.extractPrefix(name);
        String localName = QName.extractLocalName(name);
        String namespaceURI = "";
        if (prefix != null) {
            namespaceURI = (String) namespaces.get(prefix);
            if(namespaceURI == null) {
                throw new DatabaseConfigurationException("No namespace defined for prefix: " + prefix +
                    " in index definition");
            }
        }
        qname = new QName(localName, namespaceURI, prefix);
        if (isAttribute)
            qname.setNameType(ElementValue.ATTRIBUTE);
    }

    public QName getQName() {
        return qname;
    }
}
