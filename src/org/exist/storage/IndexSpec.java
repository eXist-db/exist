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

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author wolf
 */
public class IndexSpec {

    private static final String TYPE_ATTRIB = "type";
    private static final String PATH_ATTRIB = "path";
    private static final String CREATE_ELEMENT = "create";
    private static final String FULLTEXT_ELEMENT = "fulltext";
    
    private final static Logger LOG = Logger.getLogger(IndexSpec.class);
    
    private FulltextIndexSpec ftSpec = null;
    private ValueIndexSpec specs[] = null;
    
    public IndexSpec(Element index) throws DatabaseConfigurationException {
    	Map namespaces = getNamespaceMap(index);

        NodeList cl = index.getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
	            if(FULLTEXT_ELEMENT.equals(node.getLocalName())) {
	                if(ftSpec != null)
	                    throw new DatabaseConfigurationException("Only one fulltext section is allowed per index");
	                ftSpec = new FulltextIndexSpec(namespaces, (Element)node);
	            } else if(CREATE_ELEMENT.equals(node.getLocalName())) {
	                Element elem = (Element) node;
	                String path = elem.getAttribute(PATH_ATTRIB);
	                String type = elem.getAttribute(TYPE_ATTRIB);
	                ValueIndexSpec valueIdx = new ValueIndexSpec(namespaces, path, type);
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
    
    /**
     * Returns a map containing all prefix/namespace mappings declared in
     * the index element.
     * 
     * @param elem
     * @return
     */
    private Map getNamespaceMap(Element elem) {
        HashMap map = new HashMap();
        NamedNodeMap attrs = elem.getAttributes();
        for(int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if(attr.getPrefix() != null && attr.getPrefix().equals("xmlns")) {
                map.put(attr.getLocalName(), attr.getValue());
            }
        }
        return map;
    }
}
