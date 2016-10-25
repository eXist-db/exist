/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.storage;

import org.exist.Namespaces;
import org.exist.collections.CollectionConfiguration;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    private Optional<Map<String, Object>> customIndexSpecs = Optional.empty();

    public IndexSpec(DBBroker broker, Element index) throws DatabaseConfigurationException {
        read(broker, index);
    }

    /**
     * Read index configurations from an "index" element node
     *
     * @param broker
     * @param index
     * @throws DatabaseConfigurationException
     */
    public void read(final DBBroker broker, final Element index) throws DatabaseConfigurationException {
        final Map<String, String> namespaces = getNamespaceMap(index);
        final NodeList childNodes = index.getChildNodes();

        // configure custom indexes, but not if broker is null (which means we are reading
        // the default index config from conf.xml)
        if (broker != null) {
            customIndexSpecs = Optional.of(broker.getIndexController().configure(childNodes, namespaces));
        }
    }

    /**
     * Returns the configuration object registered for the non-core
     * index identified by id.
     *
     * @param id the id used to identify this index.
     * @return the configuration object registered for the index or null.
     */
    public Object getCustomIndexSpec(final String id) {
        return customIndexSpecs.map(m -> m.get(id)).orElse(null);
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
        if (parent != null) {
            elem = (Element) parent;
        }
        final Map<String, String> map = new HashMap<>();
        map.put("xml", Namespaces.XML_NS);
        getNamespaceMap(elem, map);
        return map;
    }

    private void getNamespaceMap(final Element elem, final Map<String, String> map) {
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
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                getNamespaceMap((Element) child, map);
            }
            child = child.getNextSibling();
        }
    }
}
