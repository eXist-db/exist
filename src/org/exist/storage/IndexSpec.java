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

import org.exist.Namespaces;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
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

    private FulltextIndexSpec ftSpec = null;

    private GeneralRangeIndexSpec specs[] = null;
    private Map<QName, QNameRangeIndexSpec> qnameSpecs = new TreeMap<QName, QNameRangeIndexSpec>();

    private Map<String, Object> customIndexSpecs = null;

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
        final Map<String, String> namespaces = getNamespaceMap(index);
        final NodeList childNodes = index.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (FULLTEXT_ELEMENT.equals(node.getLocalName())) {
                    ftSpec = new FulltextIndexSpec(namespaces, (Element)node);
                } else if (CREATE_ELEMENT.equals(node.getLocalName())) {
                    final Element elem = (Element) node;
                    final String type = elem.getAttribute(TYPE_ATTRIB);
                    if (elem.hasAttribute(QNAME_ATTRIB)) {
                        final String qname = elem.getAttribute(QNAME_ATTRIB);
                        final QNameRangeIndexSpec qnIdx = new QNameRangeIndexSpec(namespaces, qname, type);
                        qnameSpecs.put(qnIdx.getQName(), qnIdx);
                    } else if (elem.hasAttribute(PATH_ATTRIB)) {
                        final String path = elem.getAttribute(PATH_ATTRIB);
                        final GeneralRangeIndexSpec valueIdx = new GeneralRangeIndexSpec(namespaces, path, type);
                        addValueIndex(valueIdx);
                    } else {
                        final String error_message = "Configuration error: element " + elem.getNodeName() +
                            " must have attribute " + PATH_ATTRIB + " or " + QNAME_ATTRIB;
                        throw new DatabaseConfigurationException(error_message);
                    }
                }
            }
        }
        // configure custom indexes, but not if broker is null (which means we are reading
        // the default index config from conf.xml)
        if (broker != null)
            {customIndexSpecs = broker.getIndexController().configure(childNodes, namespaces);}
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
            for (int i = 0; i < specs.length; i++) {
                if(specs[i].matches(path))
                    {return specs[i];}
            }
        }
        return null;
    }

    public QNameRangeIndexSpec getIndexByQName(QName name) {
        return qnameSpecs.get(name);
    }

    public boolean hasIndexesByPath() {
        return specs != null && specs.length > 0;
    }

    public boolean hasIndexesByQName() {
        return qnameSpecs.size() > 0;
    }

    public List<QName> getIndexedQNames() {
        final ArrayList<QName> qnames = new ArrayList<QName>(8);
        for (final QName qname : qnameSpecs.keySet()) {
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
    private Map<String, String> getNamespaceMap(Element elem) {
        final Node parent = elem.getParentNode();
        if (parent != null)
            {elem = (Element) parent;}
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("xml", Namespaces.XML_NS);
        getNamespaceMap(elem, map);
        return map;
    }

    private void getNamespaceMap(Element elem, Map<String, String> map) {
        final NamedNodeMap attrs = elem.getAttributes();
        for(int i = 0; i < attrs.getLength(); i++) {
            final Attr attr = (Attr) attrs.item(i);
            if(attr.getPrefix() != null && "xmlns".equals(attr.getPrefix()) &&
            		!attr.getValue().equals(CollectionConfiguration.NAMESPACE)) {
                map.put(attr.getLocalName(), attr.getValue());
            }
        }
        Node child = elem.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE)
                {getNamespaceMap((Element) child, map);}
            child = child.getNextSibling();
        }
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        if (ftSpec != null)
            {result.append(ftSpec.toString()).append('\n');}
        if (specs!= null) {
            for (int i = 0 ; i < specs.length ; i++) {
                final GeneralRangeIndexSpec spec = specs[i];
                if (spec != null)
                    {result.append(spec.toString()).append('\n');}
            }
        }
        for (final QName qName : qnameSpecs.keySet()) {
            result.append(qnameSpecs.get(qName).toString()).append('\n');
        }
        return result.toString();
    }

}