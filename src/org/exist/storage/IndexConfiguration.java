/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist.sourceforge.net
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
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author wolf
 */
public class IndexConfiguration {

    private static final String DOCTYPE_ATTRIB = "doctype";
    private static final String INDEX_ELEMENT = "index";
    private final static Logger LOG = Logger.getLogger(IndexConfiguration.class);
    
    private Map indexByDoctype = new TreeMap();
    
    public IndexConfiguration(Element indexer) throws DatabaseConfigurationException {
        NodeList cl = indexer.getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element)node;
                if(INDEX_ELEMENT.equals(elem.getLocalName())) {
                    String doctype = elem.getAttribute(DOCTYPE_ATTRIB);
                    IndexSpec spec = new IndexSpec(elem);
                    LOG.debug("Registering index configuration for doctype: " + doctype);
                    indexByDoctype.put(doctype, spec);
                }
            }
        }
    }
    
    public IndexSpec getByDoctype(String doctype) {
        return (IndexSpec) indexByDoctype.get(doctype);
    }
}
