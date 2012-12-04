/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
package org.exist.collections;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.triggers.AbstractTriggerProxy;
import org.exist.collections.triggers.CollectionTriggerProxies;
import org.exist.collections.triggers.CollectionTriggerProxy;
import org.exist.collections.triggers.DocumentTriggerProxies;
import org.exist.collections.triggers.DocumentTriggerProxy;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.collections.triggers.TriggerProxy;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.ParametersExtractor;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ConfigurationClass("collection")
public class CollectionConfiguration {

    public final static String COLLECTION_CONFIG_SUFFIX = ".xconf"; 
    public final static XmldbURI COLLECTION_CONFIG_SUFFIX_URI = XmldbURI.create(COLLECTION_CONFIG_SUFFIX); 
    public final static String DEFAULT_COLLECTION_CONFIG_FILE = "collection" + COLLECTION_CONFIG_SUFFIX; 
    public final static XmldbURI DEFAULT_COLLECTION_CONFIG_FILE_URI = XmldbURI.create(DEFAULT_COLLECTION_CONFIG_FILE); 

    public final static String NAMESPACE = "http://exist-db.org/collection-config/1.0";

    private final static String ROOT_ELEMENT = "collection";
    /** First level element in a collection configuration document */
    private final static String TRIGGERS_ELEMENT = "triggers";
    private final static String TRIGGER_ELEMENT = "trigger";
    //private final static String EVENT_ATTRIBUTE = "event";
    private final static String CLASS_ATTRIBUTE = "class";
    private final static String PARAMETER_ELEMENT = "parameter";
    //private final static String PARAM_NAME_ATTRIBUTE = "name";
    //private final static String PARAM_VALUE_ATTRIBUTE = "value";

    /** First level element in a collection configuration document */
    private final static String INDEX_ELEMENT = "index";
    private final static String PERMISSIONS_ELEMENT = "default-permissions";
    private final static String GROUP_ELEMENT = "default-group";
    private final static String RESOURCE_ATTR = "resource";
    private final static String COLLECTION_ATTR = "collection";

    private final static String VALIDATION_ELEMENT = "validation";
    private final static String VALIDATION_MODE_ATTR = "mode";

    private static final Logger LOG = Logger.getLogger(CollectionConfiguration.class);

    private DocumentTriggerProxies documentTriggerProxies = null;
    private CollectionTriggerProxies collectionTriggerProxies = null;

    private IndexSpec indexSpec = null;

    private XmldbURI docName = null;
    private XmldbURI srcCollectionURI;

    private int defCollPermissions = Permission.DEFAULT_COLLECTION_PERM;
    private int defResPermissions = Permission.DEFAULT_RESOURCE_PERM;

    private String defCollGroup = null;
    private String defResGroup = null;

    private int validationMode=XMLReaderObjectFactory.VALIDATION_UNKNOWN;

    private BrokerPool pool;

    public CollectionConfiguration(BrokerPool pool) {
        this.pool = pool;
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
    protected void read(DBBroker broker, Document doc, boolean checkOnly,
            XmldbURI srcCollectionURI, XmldbURI docName) throws CollectionConfigurationException {
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
        for (int i = 0; i < childNodes.getLength(); i++) {
            node = childNodes.item(i);
            if (NAMESPACE.equals(node.getNamespaceURI())) {
                if (TRIGGERS_ELEMENT.equals(node.getLocalName())) {
                    NodeList triggers = node.getChildNodes();
                    for(int j = 0; j < triggers.getLength(); j++) {
                        node = triggers.item(j);
                        if(node.getNodeType() == Node.ELEMENT_NODE &&
                                node.getLocalName().equals(TRIGGER_ELEMENT)) {
                            List <TriggerProxy> triggerProxys = configureTrigger(
                                    (Element)node, srcCollectionURI, checkOnly);
                            if(triggerProxys != null) {
                                for(TriggerProxy triggerProxy : triggerProxys) {
                                    if(triggerProxy instanceof DocumentTriggerProxy) {
                                        getDocumentTriggerProxies().add((DocumentTriggerProxy)triggerProxy);
                                    }
                                    if(triggerProxy instanceof CollectionTriggerProxy) {
                                        getCollectionTriggerProxies().add((CollectionTriggerProxy)triggerProxy);   
                                    }
                                }
                            }
                        }
                    }
                } else if (INDEX_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    try {
                        if (indexSpec == null)
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
                                throw new CollectionConfigurationException(
                                    "Illegal value for permissions in " +
                                    "configuration document : " + e.getMessage(), e);
                            else
                                LOG.warn("Ilegal value for permissions in " +
                                    "configuration document : " + e.getMessage(), e);
                        }
                    }
                    permsOpt = elem.getAttribute(COLLECTION_ATTR);
                    if (permsOpt != null && permsOpt.length() > 0) {
                        LOG.debug("COLLECTION: " + permsOpt);
                        try {
                            defCollPermissions = Integer.parseInt(permsOpt, 8);
                        } catch (NumberFormatException e) {
                            if (checkOnly)
                                throw new CollectionConfigurationException(
                                    "Illegal value for permissions in configuration " +
                                    "document : " + e.getMessage(), e);
                            else
                                LOG.warn("Ilegal value for permissions in configuration " +
                                		"document : " + e.getMessage(), e);
                        }
                    }
                } else if (GROUP_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    String groupOpt = elem.getAttribute(RESOURCE_ATTR);
                    if (groupOpt != null && groupOpt.length() > 0) {
                        LOG.debug("RESOURCE: " + groupOpt);
                        if (pool.getSecurityManager().getGroup(groupOpt)!=null) {
                            defResGroup = groupOpt;	
                        } else {
                            //? Seems inconsistent : what does "checkOnly" means then ?
                            if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value " +
                                    "for group in configuration document : " + groupOpt);
                            else
                                LOG.warn("Ilegal value for group in configuration document : " + groupOpt);
                        }
                    }
                    groupOpt = elem.getAttribute(COLLECTION_ATTR);
                    if (groupOpt != null && groupOpt.length() > 0) {
                        LOG.debug("COLLECTION: " + groupOpt);
                        if (pool.getSecurityManager().getGroup(groupOpt)!=null) {
                            defCollGroup = groupOpt;	
                        } else {
                            //? Seems inconsistent : what does "checkOnly" means then ?
                            if (checkOnly)
                                throw new CollectionConfigurationException("Ilegal value " +
                                    "for group in configuration document : " + groupOpt);
                            else
                                LOG.warn("Ilegal value for group in configuration document : " + groupOpt);
                        }
                    }
                } else if (VALIDATION_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    String mode = elem.getAttribute(VALIDATION_MODE_ATTR);
                    if (mode==null) {
                        LOG.debug("Unable to determine validation mode in "+srcCollectionURI);
                        validationMode=XMLReaderObjectFactory.VALIDATION_UNKNOWN;
                    } else {
                        LOG.debug(srcCollectionURI + " : Validation mode="+mode);
                        validationMode=XMLReaderObjectFactory.convertValidationMode(mode);
                    }
                } else {
                    throwOrLog("Ignored node '" + node.getLocalName() +
                        "' in configuration document", checkOnly);
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

    public String getDefCollGroup(Account user) {
        return (defCollGroup != null) ? defCollGroup : user.getPrimaryGroup();
    }

    public String getDefResGroup(Account user) {
        return (defResGroup != null) ? defResGroup : user.getPrimaryGroup();
    }

    public int getValidationMode() {
        return validationMode;
    }

    public IndexSpec getIndexConfiguration() {
        return indexSpec;
    }

    public DocumentTriggerProxies getDocumentTriggerProxies() {
        if(documentTriggerProxies == null) {
             documentTriggerProxies = new DocumentTriggerProxies();
        }
        return documentTriggerProxies;
    }
    
    public CollectionTriggerProxies getCollectionTriggerProxies() {
        if(collectionTriggerProxies == null) {
             collectionTriggerProxies = new CollectionTriggerProxies();
        }
        return collectionTriggerProxies;
    }
    
    private List<TriggerProxy> configureTrigger(Element triggerElement,
        XmldbURI collectionConfigurationURI, boolean testOnly) throws CollectionConfigurationException {

        //TODO : rely on schema-driven validation -pb

        String classname = triggerElement.getAttributes().getNamedItem(CLASS_ATTRIBUTE).getNodeValue();

        try {
            Class clazz = Class.forName(classname);
            if(!Trigger.class.isAssignableFrom(clazz)) {
                throwOrLog("Trigger's class '" + classname + "' is not assignable from '" + Trigger.class + "'", testOnly);
                return null;
            }
            NodeList nlParameter = triggerElement.getElementsByTagNameNS(NAMESPACE, PARAMETER_ELEMENT);
            Map<String, List<? extends Object>> parameters = ParametersExtractor.extract(nlParameter);
            List<TriggerProxy> triggerProxys = AbstractTriggerProxy.newInstance(clazz, collectionConfigurationURI, parameters);
            return triggerProxys;
        } catch (ClassNotFoundException e) {
            if(testOnly) {
                throw new CollectionConfigurationException(e.getMessage(), e);
            } else {
                LOG.warn("Trigger class not found: " + e.getMessage(), e);
            }
        } catch (TriggerException te) {
            if(testOnly) {
                throw new CollectionConfigurationException(te.getMessage(), te);
            } else {
                LOG.warn("Trigger class not found: " + te.getMessage(), te);
            }
        }
        return null;
    }

    //TODO: code
    public boolean triggerRegistered(Class<?> triggerClass) {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (indexSpec != null)
            result.append(indexSpec.toString()).append('\n');
        return result.toString();
    }

}
