/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2013 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.RealmImpl;
import org.exist.security.utils.ConverterFrom1_0;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurationDocumentTrigger extends FilteringTrigger {

    protected Logger LOG = Logger.getLogger(getClass());

    protected DBBroker broker;

    //XXX: is it safe to delete?
    @Deprecated
    public void finish(int event, DBBroker broker, Txn txn, XmldbURI documentPath, DocumentImpl document) {

        Configuration conf;
        switch (event) {
        case REMOVE_DOCUMENT_EVENT:
            conf = Configurator.getConfigurtion(broker.getBrokerPool(), documentPath);
            if (conf != null) {
                Configurator.unregister(conf);
                //XXX: inform object that configuration was deleted
            }
            break;
        default:
            conf = Configurator.getConfigurtion(broker.getBrokerPool(), documentPath);
            if (conf != null) {
                conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
            }
            if (documentPath.toString().equals(ConverterFrom1_0.LEGACY_USERS_DOCUMENT_PATH)) {
//            	Subject currectSubject = broker.getSubject();
                try {
                	final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
//                	broker.setSubject(sm.getSystemSubject());

                    ConverterFrom1_0.convert(sm, document);
                    
                } catch (final PermissionDeniedException pde) {
                    LOG.error(pde.getMessage(), pde);
                    //TODO : raise exception ? -pb
                } catch (final EXistException ee) {
                    LOG.error(ee.getMessage(), ee);
                    //TODO : raise exception ? -pb
//                } finally {
//                	broker.setSubject(currectSubject);
                }
            }
            break;
        }
    }

    private void checkForUpdates(DBBroker broker, XmldbURI uri, DocumentImpl document) {
        final Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
        if (conf != null) {
            conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
        }
        //TODO : use XmldbURI methos ! not String.equals()
        if (uri.toString().equals(ConverterFrom1_0.LEGACY_USERS_DOCUMENT_PATH)) {
//        	Subject currectSubject = broker.getSubject();
            try {
            	final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
//            	broker.setSubject(sm.getSystemSubject());
            	
                ConverterFrom1_0.convert(sm, document);
            } catch (final PermissionDeniedException pde) {
                LOG.error(pde.getMessage(), pde);
                //TODO : raise exception ? -pb
            } catch (final EXistException ee) {
                LOG.error(ee.getMessage(), ee);
                //TODO : raise exception ? -pb
//            } finally {
//            	broker.setSubject(currectSubject);
            }
            
        }
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        this.broker = broker;
        //Nothing to do
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()) ))
            {return;}
        checkForUpdates(broker, document.getURI(), document);

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager().processPramatter(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be proccessed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        this.broker = broker;
        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()) ))
            {return;}

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager()
                .processPramatterBeforeSave(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be proccessed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()) ))
            {return;}

    	checkForUpdates(broker, document.getURI(), document);

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager().processPramatter(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be proccessed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        checkForUpdates(broker, document.getURI(), document);
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        checkForUpdates(broker, document.getURI(), document);
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        final Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
        if (conf != null) {
            Configurator.unregister(conf);
            //XXX: inform object that configuration was deleted
        }
    }

    /**
     * Mappings from User ids that were used in UnixStylePermission version of eXist-db to ACLPermission version of eXist-db
     */
    final static Map<Integer, Integer> userIdMappings = new HashMap<Integer, Integer>();
        static {
            userIdMappings.put(-1, RealmImpl.UNKNOWN_ACCOUNT_ID);
            userIdMappings.put(0, RealmImpl.SYSTEM_ACCOUNT_ID);
            userIdMappings.put(1, RealmImpl.ADMIN_ACCOUNT_ID);
            userIdMappings.put(2, RealmImpl.GUEST_ACCOUNT_ID);
        }

    /**
     * Mappings from group ids that were used in UnixStylePermission version of eXist-db to ACLPermission version of eXist-db
     */
    final static Map<Integer, Integer> groupIdMappings = new HashMap<Integer, Integer>();
        static {
            groupIdMappings.put(-1, RealmImpl.UNKNOWN_GROUP_ID);
            groupIdMappings.put(1, RealmImpl.DBA_GROUP_ID);
            groupIdMappings.put(2, RealmImpl.GUEST_GROUP_ID);
        }

    private boolean parsingName = false;
    private StringBuilder name = new StringBuilder();

    @Override
    public void startElement(String namespaceURI, String localName, String qname, Attributes attributes) throws SAXException {
        final boolean aclPermissionInUse = 
            PermissionFactory.getDefaultResourcePermission() instanceof ACLPermission;
        //map unix style user and group ids to acl style
        if (aclPermissionInUse && namespaceURI != null &&
                namespaceURI.equals(Configuration.NS) && "account".equals(localName)) {
            final Attributes newAttrs = modifyUserGroupIdAttribute(localName, attributes, userIdMappings);
            super.startElement(namespaceURI, localName, qname, newAttrs);
        } else if(aclPermissionInUse && namespaceURI != null && namespaceURI.equals(Configuration.NS) && "group".equals(localName)) {
            final Attributes newAttrs = modifyUserGroupIdAttribute(localName, attributes, groupIdMappings);
            super.startElement(namespaceURI, localName, qname, newAttrs);
        } else {
            super.startElement(namespaceURI, localName, qname, attributes);
        }
        // remember the name of the user or group when we are validating an account
        if (isValidating() && namespaceURI != null && namespaceURI.equals(Configuration.NS) && "name".equals(localName)) {
            parsingName = true;
            name.setLength(0);
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws SAXException {
        super.endElement(namespaceURI, localName, qname);
        if (isValidating() && "name".equals(localName) && namespaceURI != null && namespaceURI.equals(Configuration.NS) ) {
            parsingName = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        // remember the name of the user or group when we are validating an account
        if (isValidating() && parsingName) {
            name.append(ch, start, length);
        }
    }

    private Attributes modifyUserGroupIdAttribute(final String elementName, final Attributes attrs, final Map<Integer, Integer> idMappings) {
        final String strId = attrs.getValue("id");
        if (strId != null) {
            Integer id = Integer.parseInt(strId);

            Integer newId = idMappings.get(id);
            if(newId == null) {
                // check if an account or group with the same name exists in the database.
                // if yes, replace the id attribute to preserve the existing user or group id.
                // using the new id would corrupt the resources/collections which still reference
                // the old id.
                if (!isValidating() && name.length() > 0) {
                    if ("account".equals(elementName)) {
                        // check if there's an existing account with the same name
                        Account oldAccount = broker.getBrokerPool().getSecurityManager().getAccount(name.toString());
                        if (oldAccount != null) {
                            // yes: use id of existing account
                            newId = oldAccount.getId();
                        } else {
                            // no: check if there's another account with the same id
                            oldAccount = broker.getBrokerPool().getSecurityManager().getAccount(id);
                            if (oldAccount != null) {
                                // yes: generate new id
                                newId = broker.getBrokerPool().getSecurityManager().getNextAccountId();
                            }
                        }
                    } else if ("group".equals(elementName)) {
                        // check if there's an existing group with the same name
                        Group oldGroup = broker.getBrokerPool().getSecurityManager().getGroup(name.toString());
                        if (oldGroup != null) {
                            // yes: use id of existing group
                            newId = oldGroup.getId();
                        } else {
                            // no: check if there's another group with the same id
                            oldGroup = broker.getBrokerPool().getSecurityManager().getGroup(id);
                            if (oldGroup != null) {
                                // group with same id: generate new id
                                newId = broker.getBrokerPool().getSecurityManager().getNextGroupId();
                            }
                        }
                    }
                }
                if (newId == null) {
                    newId = id;
                }
            }
            final AttributesImpl newAttrs = new AttributesImpl(attrs);
            final int idIndex = newAttrs.getIndex("id");
            newAttrs.setAttribute(idIndex, newAttrs.getURI(idIndex), "id",
                newAttrs.getQName(idIndex), newAttrs.getType(idIndex), newId.toString());
            return newAttrs;
        }
        return attrs;
    }

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) {
	}

	@Override
	public void configure(DBBroker broker, Collection parent,
			Map<String, List<? extends Object>> parameters)
			throws TriggerException {
	}
}
