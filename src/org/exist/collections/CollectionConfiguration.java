package org.exist.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CollectionConfiguration {

	private final static String NAMESPACE = "http://exist-db.org/collection-config/1.0";
	private final static String ROOT_ELEMENT = "collection";
	private final static String TRIGGERS_ELEMENT = "triggers";
	private final static String EVENT_ATTRIBUTE = "event";
	private final static String CLASS_ATTRIBUTE = "class";
	private final static String PARAMETER_ELEMENT = "parameter";
	private final static String PARAM_NAME_ATTRIBUTE = "name";
	private final static String PARAM_VALUE_ATTRIBUTE = "value";
	
	private Trigger[] triggers = new Trigger[3];
	
	public CollectionConfiguration(Document doc) throws CollectionConfigurationException {
		Element root = doc.getDocumentElement();
		if(!(root.getNamespaceURI().equals(NAMESPACE) &&
			root.getLocalName().equals(ROOT_ELEMENT)))
			throw new CollectionConfigurationException("Wrong document root for collection.xmap");
		NodeList childNodes = root.getChildNodes();
		Node node;
		for(int i = 0; i < childNodes.getLength(); i++) {
			node = childNodes.item(i);
			if(node.getNamespaceURI().equals(NAMESPACE) &&
				node.getLocalName().equals(TRIGGERS_ELEMENT)) {
				NodeList triggers = node.getChildNodes();
				for(int j = 0; j < triggers.getLength(); j++) {
					node = triggers.item(j);
					if(node.getNodeType() == Node.ELEMENT_NODE)
						createTrigger((Element)node);
				}
			}
		}
	}
	
	public Trigger getTrigger(int eventType) {
		return triggers[eventType];
	}
	
	private void createTrigger(Element node) throws CollectionConfigurationException {
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
			if(event.equalsIgnoreCase("store")) {
				triggers[Trigger.STORE_EVENT] = instantiate(node, classAttr);
			} else if(event.equalsIgnoreCase("update")) {
				triggers[Trigger.UPDATE_EVENT] = instantiate(node, classAttr);
			} else if(event.equalsIgnoreCase("remove")) {
				triggers[Trigger.REMOVE_EVENT] = instantiate(node, classAttr);
			} else
				throw new CollectionConfigurationException("unknown event type '" + event + "'");
		}
	}
	
	private Trigger instantiate(Element node, String classname) throws CollectionConfigurationException {
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
			trigger.configure(parameters);
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
