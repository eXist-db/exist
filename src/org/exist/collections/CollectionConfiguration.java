/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.exist.collections.triggers.Trigger;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CollectionConfiguration {

	public final static String COLLECTION_CONFIG_FILE = "collection.xconf";
	
    public final static String NAMESPACE = "http://exist-db.org/collection-config/1.0";
    
	private final static String ROOT_ELEMENT = "collection";
	private final static String TRIGGERS_ELEMENT = "triggers";
	private final static String EVENT_ATTRIBUTE = "event";
	private final static String CLASS_ATTRIBUTE = "class";
	private final static String PARAMETER_ELEMENT = "parameter";
	private final static String PARAM_NAME_ATTRIBUTE = "name";
	private final static String PARAM_VALUE_ATTRIBUTE = "value";
	private final static String INDEX_ELEMENT = "index";
	private static final String DOCROOT_ATTRIBUTE = "root";
	
	private Trigger[] triggers = new Trigger[3];
	
	private IndexSpec indexSpec = null;

	private Collection collection;
	
    public CollectionConfiguration(Collection collection) {
    	this.collection = collection;
    }
    
	public CollectionConfiguration(DBBroker broker, Collection collection, Document doc) 
    throws CollectionConfigurationException {
		this.collection = collection;
		read(broker, doc);
	}
	
	/**
     * @param broker
     * @param collection
     * @param doc
     * @throws CollectionConfigurationException
     */
    protected void read(DBBroker broker, Document doc) throws CollectionConfigurationException {
        Element root = doc.getDocumentElement();
		if(!(NAMESPACE.equals(root.getNamespaceURI()) &&
			ROOT_ELEMENT.equals(root.getLocalName())))
			throw new CollectionConfigurationException("Wrong document root for collection configuration. " +
					"The root element should be " + ROOT_ELEMENT + " in namespace " + NAMESPACE);
		NodeList childNodes = root.getChildNodes();
		Node node;
		for(int i = 0; i < childNodes.getLength(); i++) {
			node = childNodes.item(i);
			if(NAMESPACE.equals(node.getNamespaceURI())) {
			    if(TRIGGERS_ELEMENT.equals(node.getLocalName())) {
					NodeList triggers = node.getChildNodes();
					for(int j = 0; j < triggers.getLength(); j++) {
						node = triggers.item(j);
						if(node.getNodeType() == Node.ELEMENT_NODE)
							createTrigger(broker, (Element)node);
					}
			    } else if(INDEX_ELEMENT.equals(node.getLocalName())) {
			        Element elem = (Element) node;
                    try {
                        if(indexSpec == null)
                            indexSpec = new IndexSpec(elem);
                        else
                            indexSpec.read(elem);
                    } catch (DatabaseConfigurationException e) {
                        throw new CollectionConfigurationException(e.getMessage(), e);
                    }
			    }
			}
		}
    }

    public Collection getCollection() {
    	return collection;
    }
    
    public IndexSpec getIndexConfiguration() {
        return indexSpec;
    }
	
	public Trigger getTrigger(int eventType) {
		return triggers[eventType];
	}
	
	private void createTrigger(DBBroker broker, Element node) 
    throws CollectionConfigurationException {
		String eventAttr = node.getAttribute(EVENT_ATTRIBUTE);
		if(eventAttr == null)
			throw new CollectionConfigurationException("trigger requires an attribute 'event'");
		String classAttr = node.getAttribute(CLASS_ATTRIBUTE);
		if(classAttr == null)
			throw new CollectionConfigurationException("trigger requires an attribute 'class'");
		StringTokenizer tok = new StringTokenizer(eventAttr, ", ");
		String event;
		Trigger trigger;
		while(tok.hasMoreTokens()) {
			event = tok.nextToken();
			System.out.println("Registering trigger " + classAttr + " for event " + event);
			if(event.equalsIgnoreCase("store")) {
				triggers[Trigger.STORE_DOCUMENT_EVENT] = instantiate(broker, node, classAttr);
			} else if(event.equalsIgnoreCase("update")) {
				triggers[Trigger.UPDATE_DOCUMENT_EVENT] = instantiate(broker, node, classAttr);
			} else if(event.equalsIgnoreCase("remove")) {
				triggers[Trigger.REMOVE_DOCUMENT_EVENT] = instantiate(broker, node, classAttr);
			} else
				throw new CollectionConfigurationException("unknown event type '" + event + "'");
		}
	}
	
	private Trigger instantiate(DBBroker broker, Element node, String classname) 
    throws CollectionConfigurationException {
		try {
			Class clazz = Class.forName(classname);
			if(!Trigger.class.isAssignableFrom(clazz))
				throw new CollectionConfigurationException("supplied class is not a subclass of org.exist.collections.Trigger");
			Trigger trigger = (Trigger)clazz.newInstance();
			NodeList nodes = node.getChildNodes();
			Node next;
			Element param;
			String name, value;
			Map parameters = new HashMap(5);
			for(int i = 0; i < nodes.getLength(); i++) {
				next = nodes.item(i);
				if(next.getNodeType() == Node.ELEMENT_NODE &&
					next.getNamespaceURI().equals(NAMESPACE) &&
					next.getLocalName().equals(PARAMETER_ELEMENT)) {
					param = (Element)next;
					name = param.getAttribute(PARAM_NAME_ATTRIBUTE);
					value = param.getAttribute(PARAM_VALUE_ATTRIBUTE);
					if(name == null || value == null)
						throw new CollectionConfigurationException("element parameter requires attributes " +
							"'name' and 'value'");
					parameters.put(name, value);
				}
			}
			trigger.configure(broker, collection, parameters);
			return trigger;
		} catch (ClassNotFoundException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		} catch (InstantiationException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		}
	}
}
