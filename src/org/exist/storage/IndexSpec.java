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

import org.apache.log4j.Logger;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author wolf
 */
public class IndexSpec {

    private final static Logger LOG = Logger.getLogger(IndexSpec.class);
    
    private FulltextIndexSpec ftSpec = null;
    private ValueIndexSpec specs[] = null;
    
    public IndexSpec(Element index) throws DatabaseConfigurationException {
        NodeList cl = index.getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
	            if("fulltext".equals(node.getLocalName())) {
	                if(ftSpec != null)
	                    throw new DatabaseConfigurationException("Only one fulltext section is allowed per index");
	                ftSpec = new FulltextIndexSpec((Element)node);
	            } else if("create".equals(node.getLocalName())) {
	                Element elem = (Element) node;
	                String path = elem.getAttribute("path");
	                String type = elem.getAttribute("type");
	                LOG.debug("creating value index for " + path + "; type: " + type);
	                ValueIndexSpec valueIdx = new ValueIndexSpec(path, type);
	                addValueIndex(valueIdx);
	            }
            }
        }
    }

    public FulltextIndexSpec getFulltextIndexSpec() {
        return ftSpec;
    }
    
    public ValueIndexSpec getIndexByPath(NodePath path) {
        if(specs != null) {
	        for(int i = 0; i < specs.length; i++) {
	            if(specs[i].matches(path))
	                return specs[i];
	        }
        }
        return null;
    }
    
    /**
     * @param valueIdx
     */
    private void addValueIndex(ValueIndexSpec valueIdx) {
        if(specs == null) {
            specs = new ValueIndexSpec[1];
            specs[0] = valueIdx;
        } else {
            ValueIndexSpec nspecs[] = new ValueIndexSpec[specs.length + 1];
            System.arraycopy(specs, 0, nspecs, 0, specs.length);
            nspecs[specs.length] = valueIdx;
            specs = nspecs;
        }
    }
}
