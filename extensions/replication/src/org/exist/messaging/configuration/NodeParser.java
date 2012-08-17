/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.messaging.configuration;

import java.util.HashMap;
import java.util.Map;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Helper class for parsing an exist-db node.<BR>
 *
 * COnverts a structure like
 *
 * <A> <B><C>dd</C></B> <E><F>gg</F></E> </A>
 *
 * into a map like
 *
 * A.B.C = dd A.B.D = gg
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public abstract class NodeParser {

    private Map<String, String> valueMap = new HashMap<String, String>();
    private String rootName;

    public String getRawConfigurationItem(String key) {
        return valueMap.get(key);
    }

    public String getConfigurationItem(String key) {
        return valueMap.get(rootName + "." + key);
    }

    public String getRootName() {
        return rootName;
    }

    public void parseDocument(NodeValue configNode) {
        Node doc = configNode.getNode();
        rootName = doc.getLocalName();
        parseNode(doc, rootName);
    }
    
    public Map<String, String> getRawValueMap(){
        return valueMap;
    }
    
    public Map<String, String> getValueMap(){
        
        Map<String, String> retVal = new HashMap<String, String>();
        
        String prefix=rootName + ".";
        int offset = prefix.length();
        
        for(String key: valueMap.keySet()){
            
            String value=valueMap.get(key);
            
            if(key.startsWith(prefix)){
                key = key.substring(offset);
            }
            
            retVal.put(key, value);
        }
        
        return retVal;
    }

    /**
     * Iterate over all child elements in node, if no child nodes present, read
     * value of element.
     *
     * @param node Node to be parsed
     * @param path path to current node, like a.b.c
     */
    private void parseNode(Node node, String path) {

        NodeList nodeList = node.getChildNodes();
        int length = nodeList.getLength();

        if (length > 0) {

            for (int i = 0; i < length; i++) {

                Node child = nodeList.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    parseNode(child, path + "." + child.getLocalName());

                } else if (child.getNodeType() == Node.TEXT_NODE) {
                    valueMap.put(path, node.getNodeValue());

                } else {
                    // ignore
                }
            }

        }
    }
}
