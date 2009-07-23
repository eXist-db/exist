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
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Top class for index definitions as specified in a collection configuration
 * or the main configuration file. The IndexSpec for a given collection can be retrieved through method
 * {@link org.exist.collections.Collection#getIndexConfiguration(DBBroker)}.
 *  
 *  An index definition should have the following structure:
 *  
 *  <pre>
 *  &lt;index index-depth="idx-depth"&gt;
 *      &lt;fulltext default="all|none" attributes="true|false"&gt;
 *          &lt;include path="node-path"/&gt;
 *          &lt;exclude path="node-path"/&gt;
 *      &lt;/fulltext&gt;
 *      &lt;create path="node-path" type="schema-type"&gt;
 *  &lt;/index&gt;
 *  </pre>
 *  
 * @author wolf
 */
public class IndexSpec {

    private static final String TYPE_ATTRIB = "type";
    private static final String PATH_ATTRIB = "path";
    private static final String CREATE_ELEMENT = "create";
    private static final String QNAME_ATTRIB = "qname";
    private static final String FULLTEXT_ELEMENT = "fulltext";

    private final static Logger LOG = Logger.getLogger(IndexSpec.class);

    private FulltextIndexSpec ftSpec = null;

    private GeneralRangeIndexSpec specs[] = null;
    private Map qnameSpecs = new TreeMap();

    private Map customIndexSpecs = null;

    public IndexSpec(DBBroker broker, Element index) throws DatabaseConfigurationException {
        read(broker, index);
    }
    
    /**
     * Read index configurations from an "index" element node. The node should have
     * exactly one "fulltext" child node and zero or more "create" nodes. The "fulltext"
     * section  is forwarded to class {@link FulltextIndexSpec}. The "create" elements
     * add a {@link GeneralRangeIndexSpec} to the current configuration.
     *  
     * @param index
     * @throws DatabaseConfigurationException
     */
    public void read(DBBroker broker, Element index) throws DatabaseConfigurationException {
        Map namespaces = getNamespaceMap(index);
		
        NodeList cl = index.getChildNodes();
        for(int i = 0; i < cl.getLength(); i++) {
            Node node = cl.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
	            if(FULLTEXT_ELEMENT.equals(node.getLocalName())) {
	                ftSpec = new FulltextIndexSpec(namespaces, (Element)node);
	            } else if(CREATE_ELEMENT.equals(node.getLocalName())) {
	                Element elem = (Element) node;
	                String type = elem.getAttribute(TYPE_ATTRIB);
	                if (elem.hasAttribute(QNAME_ATTRIB)) {
	                	String qname = elem.getAttribute(QNAME_ATTRIB);
	                	QNameRangeIndexSpec qnIdx = new QNameRangeIndexSpec(namespaces, qname, type);
		                qnameSpecs.put(qnIdx.getQName(), qnIdx);
	                } else if (elem.hasAttribute(PATH_ATTRIB)) {
	                	String path = elem.getAttribute(PATH_ATTRIB);
	                	GeneralRangeIndexSpec valueIdx = new GeneralRangeIndexSpec(namespaces, path, type);
	                	addValueIndex(valueIdx);
	                } else {
	                	String error_message = "Configuration error: element " + elem.getNodeName() +
	                		" must have attribute " + PATH_ATTRIB + " or " + QNAME_ATTRIB;
	                	throw new DatabaseConfigurationException(error_message);
	                }
	            } else {
	            	LOG.info("Unable to process index configuration element :" + node.getLocalName());
	            }
            }
        }

        // configure custom indexes, but not if broker is null (which means we are reading
        // the default index config from conf.xml)
        if (broker != null)
            customIndexSpecs = broker.getIndexController().configure(cl, namespaces);
    }
	
    /**
     * Returns the fulltext index configuration object for the current
     * configuration.
     */
    public FulltextIndexSpec getFulltextIndexSpec() {
        return ftSpec;
    }

    /**
     * Returns the configuration object registered for the non-core
     * index identified by id.
     *
     * @param id the id used to identify this index.
     * @return the configuration object registered for the index or null.
     */
    public Object getCustomIndexSpec(String id) {
        return customIndexSpecs == null ? null : customIndexSpecs.get(id);
    }

    /**
     * Returns the {@link GeneralRangeIndexSpec} defined for the given
     * node path or null if no index has been configured.
     * 
     * @param path
     */
    public GeneralRangeIndexSpec getIndexByPath(NodePath path) {
        if(specs != null) {
	        for(int i = 0; i < specs.length; i++) {
	            if(specs[i].matches(path))
	                return specs[i];
	        }
        }
        return null;
    }
    
    public QNameRangeIndexSpec getIndexByQName(QName name) {
    	return (QNameRangeIndexSpec) qnameSpecs.get(name);
    }

    public boolean hasIndexesByPath() {
        return specs != null && specs.length > 0;
    }

    public boolean hasIndexesByQName() {
        return qnameSpecs.size() > 0;
    }
    
    public List getIndexedQNames() {
        ArrayList qnames = new ArrayList(8);
        for (Iterator i = qnameSpecs.keySet().iterator(); i.hasNext(); ) {
            QName qname = (QName) i.next();
            qnames.add(qname);
        }
        return qnames;
    }
    
    /**
     * Add a {@link GeneralRangeIndexSpec}.
     * 
     * @param valueIdx
     */
    private void addValueIndex(GeneralRangeIndexSpec valueIdx) {
        if(specs == null) {
            specs = new GeneralRangeIndexSpec[1];
            specs[0] = valueIdx;
        } else {
            GeneralRangeIndexSpec nspecs[] = new GeneralRangeIndexSpec[specs.length + 1];
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
     * @return The namespaces map.
     */
    private Map getNamespaceMap(Element elem) {
        HashMap map = new HashMap();
        map.put("xml", Namespaces.XML_NS);
        NamedNodeMap attrs = elem.getAttributes();
        for(int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if(attr.getPrefix() != null && attr.getPrefix().equals("xmlns")) {
                map.put(attr.getLocalName(), attr.getValue());
            }
        }
        return map;
    }
    
    public String toString() {
		StringBuilder result = new StringBuilder();
		if (ftSpec != null)
			result.append(ftSpec.toString()).append('\n');
		if(specs!= null) {
			for (int i = 0 ; i < specs.length ; i++) {
				GeneralRangeIndexSpec spec = specs[i];
				if (spec != null)
					result.append(spec.toString()).append('\n');
			}
		}		
		Iterator i = qnameSpecs.keySet().iterator();		
		while (i.hasNext()) {
			result.append(qnameSpecs.get(i.next()).toString()).append('\n');
		}		
		return result.toString();
    }
    
}