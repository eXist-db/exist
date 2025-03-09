/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.Namespaces;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.QName;
import org.exist.dom.TypedQNameComparator;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Top class for index definitions as specified in a collection configuration
 * or the main configuration file. The IndexSpec for a given collection can be retrieved through method
 * {@link org.exist.collections.Collection#getIndexConfiguration(DBBroker)}.
 *  
 *  An index definition should have the following structure:
 *  
 *  <pre>
 *  &lt;index index-depth="idx-depth"&gt;
 *      &lt;create path="node-path" type="schema-type"&gt;
 *  &lt;/index&gt;
 *  </pre>
 *  
 * @author wolf
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class IndexSpec {

    private static final String TYPE_ATTRIB = "type";
    private static final String PATH_ATTRIB = "path";
    private static final String CREATE_ELEMENT = "create";
    private static final String QNAME_ATTRIB = "qname";

    private GeneralRangeIndexSpec specs[] = null;
    private Map<QName, QNameRangeIndexSpec> qnameSpecs = new TreeMap<>(new TypedQNameComparator());

    private Map<String, Object> customIndexSpecs = null;

    public IndexSpec(DBBroker broker, Element index) throws DatabaseConfigurationException {
        read(broker, index);
    }

    /**
     * Read index configurations from an "index" element node.
     * The node should have zero or more "create" nodes.
     * The "create" elements add a {@link GeneralRangeIndexSpec} to the current configuration.
     *  
     * @param index index configuration
     * @param broker the eXist-db DBBroker
     * @throws DatabaseConfigurationException in response to an eXist-db configuration error
     *
     */
    public void read(DBBroker broker, Element index) throws DatabaseConfigurationException {
        final Map<String, String> namespaces = getNamespaceMap(index);
        final NodeList childNodes = index.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (CREATE_ELEMENT.equals(node.getLocalName())) {
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
     * @return the {@link GeneralRangeIndexSpec} defined for the given
     * node path or null if no index has been configured.
     * 
     * @param path given node path
     */
    public GeneralRangeIndexSpec getIndexByPath(NodePath path) {
        if(specs != null) {
            for (GeneralRangeIndexSpec spec : specs) {
                if (spec.matches(path)) {
                    return spec;
                }
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
        return new ArrayList<>(qnameSpecs.keySet());
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
        final HashMap<String, String> map = new HashMap<>();
        map.put("xml", Namespaces.XML_NS);
        getNamespaceMap(elem, map);
        return map;
    }

    private void getNamespaceMap(Element elem, Map<String, String> map) {
        final NamedNodeMap attrs = elem.getAttributes();
        for(int i = 0; i < attrs.getLength(); i++) {
            final Attr attr = (Attr) attrs.item(i);
            if (attr.getPrefix() != null
                && "xmlns".equals(attr.getPrefix())
                && !attr.getValue().equals(CollectionConfiguration.NAMESPACE)
            ) {
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
        if (specs!= null) {
            for (final GeneralRangeIndexSpec spec : specs) {
                if (spec != null) {
                    result.append(spec).append('\n');
                }
            }
        }
        for (final Map.Entry<QName, QNameRangeIndexSpec> qNameSpec : qnameSpecs.entrySet()) {
            result.append(qNameSpec.getValue().toString()).append('\n');
        }
        return result.toString();
    }
}
