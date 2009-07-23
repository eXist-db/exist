/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import org.apache.log4j.Logger;
import org.exist.collections.triggers.Trigger;
import org.exist.dom.DocumentImpl;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CollectionConfiguration {

    public final static String COLLECTION_CONFIG_SUFFIX = ".xconf"; 
    public final static XmldbURI COLLECTION_CONFIG_SUFFIX_URI = XmldbURI.create(COLLECTION_CONFIG_SUFFIX); 
    public final static String DEFAULT_COLLECTION_CONFIG_FILE = "collection" + COLLECTION_CONFIG_SUFFIX; 
    public final static XmldbURI DEFAULT_COLLECTION_CONFIG_FILE_URI = XmldbURI.create(DEFAULT_COLLECTION_CONFIG_FILE); 
    	
    public final static String NAMESPACE = "http://exist-db.org/collection-config/1.0";
    
	private final static String ROOT_ELEMENT = "collection";
	/** First level element in a collection configuration document */
	private final static String TRIGGERS_ELEMENT = "triggers";
	private final static String EVENT_ATTRIBUTE = "event";
	private final static String CLASS_ATTRIBUTE = "class";
	private final static String PARAMETER_ELEMENT = "parameter";
	private final static String PARAM_NAME_ATTRIBUTE = "name";
	private final static String PARAM_VALUE_ATTRIBUTE = "value";
	/** First level element in a collection configuration document */
	private final static String INDEX_ELEMENT = "index";
	private final static String PERMISSIONS_ELEMENT = "default-permissions";
	private final static String GROUP_ELEMENT = "default-group";
	private final static String RESOURCE_ATTR = "resource";
	private final static String COLLECTION_ATTR = "collection";
    
    private final static String VALIDATION_ELEMENT = "validation";
    private final static String VALIDATION_MODE_ATTR = "mode";
	
	private static final Logger LOG = Logger.getLogger(CollectionConfiguration.class);

	private TriggerConfig[] triggers = new TriggerConfig[6];

	private IndexSpec indexSpec = null;
    
    private XmldbURI docName = null;
    private XmldbURI srcCollectionURI;
	
	private int defCollPermissions;
	private int defResPermissions;
    
	private String defCollGroup = null;
	private String defResGroup = null;
    
    private int validationMode=XMLReaderObjectFactory.VALIDATION_UNKNOWN;
    
    private SecurityManager secman;

    public CollectionConfiguration(BrokerPool pool) {
    	secman = pool.getSecurityManager();
    	defResPermissions = secman.getResourceDefaultPerms();
		defCollPermissions = secman.getCollectionDefaultPerms();
     }
    
    
	public static boolean isCollectionConfigDocument(XmldbURI docName) {
		return docName.endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX_URI);
	}
	
	public static boolean isCollectionConfigDocument(DocumentImpl doc ) {
		XmldbURI docName = doc.getURI();
		return isCollectionConfigDocument( docName );
	}
	
	/**
     * @param broker
     * @param srcCollectionURI The collection from which the document is being read.  This
     * is not necessarily the same as this.collection.getURI() because the
     * source document may have come from a parent collection.
     * @param docName The name of the document being read
     * @param doc collection configuration document
     * @throws CollectionConfigurationException
     */
    protected void read(DBBroker broker, Document doc, boolean checkOnly, XmldbURI srcCollectionURI, XmldbURI docName) throws CollectionConfigurationException {
        if (!checkOnly) {
            this.docName = docName;
            this.srcCollectionURI = srcCollectionURI;
        }
        
        Element root = doc.getDocumentElement();
        if (root == null) {
            throwOrLog("Configuration document can not be parsed", checkOnly);
            return;
        }
        if (!ROOT_ELEMENT.equals(root.getLocalName())) {
            throwOrLog("Expected element '" + ROOT_ELEMENT +
                    "' in configuration document. Got element '" + root.getLocalName() + "'", checkOnly);
            return;
        }
        if(!NAMESPACE.equals(root.getNamespaceURI())) {
            throwOrLog("Expected namespace '" + NAMESPACE +
                    "' for element '" + PARAMETER_ELEMENT + 
                    "' in configuration document. Got '" + root.getNamespaceURI() + "'", checkOnly);
            return;
        }
        
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
							createTrigger(broker, (Element)node, checkOnly);
					}
                    
			    } else if(INDEX_ELEMENT.equals(node.getLocalName())) {
			        Element elem = (Element) node;
                    try {
                        if(indexSpec == null)
                            indexSpec = new IndexSpec(broker, elem);
                        else
                            indexSpec.read(broker, elem);
                    } catch (DatabaseConfigurationException e) {
                        if (checkOnly)
                            throw new CollectionConfigurationException(e.getMessage(), e);
                        else
                            LOG.warn(e.getMessage(), e);
                    }
                    
			    } else if (PERMISSIONS_ELEMENT.equals(node.getLocalName())) {
			    	Element elem = (Element) node;
			    	String permsOpt = elem.getAttribute(RESOURCE_ATTR);
					if (permsOpt != null && permsOpt.length() > 0) {
						LOG.debug("RESOURCE: " + permsOpt);
						try {
							defResPermissions = Integer.parseInt(permsOpt, 8);
						} catch (NumberFormatException e) {
                            if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value for permissions in configuration document : " +
								    e.getMessage(), e);
                            else
                                LOG.warn("Ilegal value for permissions in configuration document : " +
								    e.getMessage(), e);
                        }
					}
					permsOpt = elem.getAttribute(COLLECTION_ATTR);
					if (permsOpt != null && permsOpt.length() > 0) {
						LOG.debug("COLLECTION: " + permsOpt);
						try {
							defCollPermissions = Integer.parseInt(permsOpt, 8);
						} catch (NumberFormatException e) {
							if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value for permissions in configuration document : " +
								    e.getMessage(), e);
                            else
                                LOG.warn("Ilegal value for permissions in configuration document : " +
								    e.getMessage(), e);
						}
					}
                    
			    } else if (GROUP_ELEMENT.equals(node.getLocalName())) {
			    	Element elem = (Element) node;
			    	String groupOpt = elem.getAttribute(RESOURCE_ATTR);
					if (groupOpt != null && groupOpt.length() > 0) {
						LOG.debug("RESOURCE: " + groupOpt);
						if (secman.getGroup(groupOpt)!=null){
							defResGroup = groupOpt;	
						} else {
                            if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value for group in configuration document : " + groupOpt);
                            else
                                LOG.warn("Ilegal value for group in configuration document : " + groupOpt);
						}
					}
					groupOpt = elem.getAttribute(COLLECTION_ATTR);
					if (groupOpt != null && groupOpt.length() > 0) {
						LOG.debug("COLLECTION: " + groupOpt);
						if (secman.getGroup(groupOpt)!=null){
							defCollGroup = groupOpt;	
						} else {
                            if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value for group in configuration document : " + groupOpt);
                            else
                                LOG.warn("Ilegal value for group in configuration document : " + groupOpt);
						}
					}
                    
                } else if (VALIDATION_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    String mode = elem.getAttribute(VALIDATION_MODE_ATTR);
                    if(mode==null){
                        LOG.debug("Unable to determine validation mode in "+srcCollectionURI);
                        validationMode=XMLReaderObjectFactory.VALIDATION_UNKNOWN;
                    } else {
                        LOG.debug(srcCollectionURI + " : Validation mode="+mode);
                        validationMode=XMLReaderObjectFactory.convertValidationMode(mode);
                    }                
                    
			    } else {
                    throwOrLog("Ignored node '" + node.getLocalName() + "' in configuration document", checkOnly);
                    //TODO : throw an exception like above ? -pb
                }
			} else if (node.getNodeType() == Node.ELEMENT_NODE) {
                throwOrLog("Ignored node '" + node.getLocalName() + "' in namespace '" +
                        node.getNamespaceURI() + "' in configuration document", checkOnly);
            }
		}
    }

    private void throwOrLog(String message, boolean throwExceptions) throws CollectionConfigurationException {
        if (throwExceptions)
            throw new CollectionConfigurationException(message);
        else
            LOG.warn(message);
    }

    public XmldbURI getDocName() {
        return docName;
    }

    protected void setIndexConfiguration(IndexSpec spec) {
        this.indexSpec = spec;
    }
    
    public XmldbURI getSourceCollectionURI() {
        return srcCollectionURI;
    }    

    public int getDefCollPermissions() {
    	return defCollPermissions;
    }
    
    public int getDefResPermissions() {
    	return defResPermissions;
    }
    
    public String getDefCollGroup(User user) {
    	return (defCollGroup != null) ? defCollGroup : user.getPrimaryGroup();
    }
    
    public String getDefResGroup(User user) {
    	return (defResGroup != null) ? defResGroup : user.getPrimaryGroup();
    }
    
    public int getValidationMode() {
        return validationMode;
    }
    
    public IndexSpec getIndexConfiguration() {
        return indexSpec;
    }

    public Trigger newTrigger(int eventType, DBBroker broker, Collection collection) throws org.exist.collections.CollectionConfigurationException {
        TriggerConfig config = getTriggerConfiguration(eventType);
        if (config != null)
            return config.newInstance(broker, collection);
        return null;
    }

    public TriggerConfig getTriggerConfiguration(int eventType) {
		return triggers[eventType];
	}

    public boolean triggerRegistered(Class triggerClass) {
        for (int i = 0; i < triggers.length; i++) {
            if (triggers[i] != null && triggers[i].getTriggerClass() == triggerClass)
                return true;
        }
        return false;
    }

	private void createTrigger(DBBroker broker, Element node, boolean testConfig)
            throws CollectionConfigurationException {
        
		String eventAttr = node.getAttribute(EVENT_ATTRIBUTE);
		if(eventAttr == null) {
			throwOrLog("'" + node.getNodeName() +
                    "' requires an attribute '"+ EVENT_ATTRIBUTE + "'", testConfig);
            return;
        }
        String classAttr = node.getAttribute(CLASS_ATTRIBUTE);
		if(classAttr == null) {
			throwOrLog("'" + node.getNodeName() +
                    "' requires an attribute '"+ CLASS_ATTRIBUTE + "'", testConfig);
            return;
        }
        
        TriggerConfig trigger = instantiate(broker, node, classAttr, testConfig);
        if (!testConfig) {
            StringTokenizer tok = new StringTokenizer(eventAttr, ", ");
            String event;
            while(tok.hasMoreTokens()) {
                event = tok.nextToken();
                LOG.debug("Registering trigger '" + classAttr + "' for event '" + event + "'");
                if(event.equalsIgnoreCase("store")) {
                    if (triggers[Trigger.STORE_DOCUMENT_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.STORE_DOCUMENT_EVENT] = trigger;
                } else if(event.equalsIgnoreCase("update")) {
                    if (triggers[Trigger.UPDATE_DOCUMENT_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.UPDATE_DOCUMENT_EVENT] = trigger;
                } else if(event.equalsIgnoreCase("remove")) {
                    if (triggers[Trigger.REMOVE_DOCUMENT_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.REMOVE_DOCUMENT_EVENT] = trigger;
                } else if(event.equalsIgnoreCase("create-collection")) {
                    if (triggers[Trigger.CREATE_COLLECTION_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.CREATE_COLLECTION_EVENT] = trigger;
                } else if(event.equalsIgnoreCase("rename-collection")) {
                    if (triggers[Trigger.RENAME_COLLECTION_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.RENAME_COLLECTION_EVENT] = trigger;
                } else if(event.equalsIgnoreCase("delete-collection")) {
                    if (triggers[Trigger.DELETE_COLLECTION_EVENT] != null)
                        LOG.warn("Trigger '" + classAttr + "' already registered");
                    triggers[Trigger.DELETE_COLLECTION_EVENT] = trigger;
                } else
                    LOG.warn("Unknown event type '" + event + "' in trigger '" + classAttr + "'");
            }
        }
    }
	
	private TriggerConfig instantiate(DBBroker broker, Element node, String classname, boolean testOnly)
            throws CollectionConfigurationException {
		try {
			Class clazz = Class.forName(classname);
			if(!Trigger.class.isAssignableFrom(clazz)) {
				throwOrLog("Trigger's class '" + classname + "' is not assignable from '" + Trigger.class + "'", testOnly);
                return null;
            }
            TriggerConfig triggerConf = new TriggerConfig(clazz);
			NodeList nodes = node.getElementsByTagNameNS(NAMESPACE, PARAMETER_ELEMENT);
            //TODO : rely on schema-driven validation -pb
            if (nodes.getLength() > 0) {
                Map parameters = new HashMap(nodes.getLength()); 
                for (int i = 0 ; i < nodes.getLength();  i++) {
                    Element param = (Element)nodes.item(i);
                    //TODO : rely on schema-driven validation -pb
                    String name = param.getAttribute(PARAM_NAME_ATTRIBUTE);
                    if(name == null) {
                        throwOrLog("Expected attribute '" + PARAM_NAME_ATTRIBUTE +
                                "' for element '" + PARAMETER_ELEMENT + "' in trigger's configuration.", testOnly);
                    } else {
                        String value = param.getAttribute(PARAM_VALUE_ATTRIBUTE);
                        if(value == null) {
                            throwOrLog("Expected attribute '" + PARAM_VALUE_ATTRIBUTE +
                                    "' for element '" + PARAMETER_ELEMENT + "' in trigger's configuration.", testOnly);
                        } else
                            parameters.put(name, value);
                    }
                }
                triggerConf.setParameters(parameters);
            }
            return triggerConf;
        } catch (ClassNotFoundException e) {
            if (testOnly)
                throw new CollectionConfigurationException(e.getMessage(), e);
            else
                LOG.warn("Trigger class not found: " + e.getMessage(), e);
		}
        return null;
    }
	
	public String toString() {
		StringBuilder result = new StringBuilder();
		if (indexSpec != null)
			result.append(indexSpec.toString()).append('\n');		
		for (int i = 0 ; i < triggers.length; i++) {
			TriggerConfig trigger = triggers[i];
			if (trigger != null) {
				switch (i) {
					case Trigger.STORE_DOCUMENT_EVENT : result.append("store document trigger");
					case Trigger.UPDATE_DOCUMENT_EVENT : result.append("update document trigger");
					case Trigger.REMOVE_DOCUMENT_EVENT : result.append("remove document trigger");
					case Trigger.CREATE_COLLECTION_EVENT : result.append("create collection trigger");		
					case Trigger.RENAME_COLLECTION_EVENT : result.append("rename collection trigger");
					case Trigger.DELETE_COLLECTION_EVENT : result.append("delete collection trigger");		
				}			
				result.append('\t').append(trigger.toString()).append('\n');
			}
		}		
		return result.toString();
	}

    public static class TriggerConfig {

        private Class clazz;
        private Map parameters;

        public TriggerConfig(Class clazz) {
            this.clazz = clazz;
        }

        public Trigger newInstance(DBBroker broker, Collection collection) throws CollectionConfigurationException {
            try {
                Trigger trigger = (Trigger) clazz.newInstance();
                trigger.configure(broker, collection, parameters);
                return trigger;
            } catch (InstantiationException e) {
                throw new CollectionConfigurationException(e.getMessage(), e);
            } catch (IllegalAccessException e) {
                throw new CollectionConfigurationException(e.getMessage(), e);
            }
        }

        public Map getParameters() {
            return parameters;
        }

        public void setParameters(Map parameters) {
            this.parameters = parameters;
        }

        public Class getTriggerClass() {
            return clazz;
        }
    }
}
