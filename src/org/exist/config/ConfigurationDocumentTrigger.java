/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
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

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.DeferrableFilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.RealmImpl;
import org.exist.security.utils.ConverterFrom1_0;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.sax.event.SAXEvent;
import org.exist.util.sax.event.contenthandler.Characters;
import org.exist.util.sax.event.contenthandler.Element;
import org.exist.util.sax.event.contenthandler.StartElement;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Amongst other things, this trigger defers immediate updates to Principals
 * (Accounts or Groups) until it has enough information to determine
 * if such an update would cause a principal id or name collision.
 *
 * If a collision is detected, then it attempts to resolve the collision,
 * before the deferred updates are applied.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class ConfigurationDocumentTrigger extends DeferrableFilteringTrigger {

    private final static String ID_ATTR = "id";

    protected Logger LOG = Logger.getLogger(getClass());

    private DBBroker broker = null;

    /*
    Used for holding a pre-allocated id for either an account or group
    */
    private final PreAllocatedIdReceiver preAllocatedId = new PreAllocatedIdReceiver();

    /*
    Are we creating or updating a document?
    */
    private boolean createOrUpdate = false;

    /*
    Guard used to prevent processing group elements
    within account elements as though they were standalone
    group elements
    */
    private boolean processingAccount = false;


    @Deprecated
    public void finish(final int event, final DBBroker broker, final Txn txn, final XmldbURI documentPath, final DocumentImpl document) {
        
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
                try {
                	final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    ConverterFrom1_0.convert(sm, document);
                } catch (final PermissionDeniedException pde) {
                    LOG.error(pde.getMessage(), pde);
                    //TODO : raise exception ? -pb
                } catch (final EXistException ee) {
                    LOG.error(ee.getMessage(), ee);
                    //TODO : raise exception ? -pb
                }
            }
            break;
        }
    }

    private void checkForUpdates(final DBBroker broker, final XmldbURI uri, final DocumentImpl document) {
        final Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
        if (conf != null) {
            conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
        }

        //TODO : use XmldbURI methos ! not String.equals()
        if (uri.toString().equals(ConverterFrom1_0.LEGACY_USERS_DOCUMENT_PATH)) {
            try {
            	final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                ConverterFrom1_0.convert(sm, document);
            } catch (final PermissionDeniedException pde) {
                LOG.error(pde.getMessage(), pde);
                //TODO : raise exception ? -pb
            } catch (final EXistException ee) {
                LOG.error(ee.getMessage(), ee);
                //TODO : raise exception ? -pb
            }
        }
    }

    @Override
    public void beforeCreateDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
        this.createOrUpdate = true;
        this.broker = broker;
    }

    @Override
    public void afterCreateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()) ))
            {return;}

        checkForUpdates(broker, document.getURI(), document);

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager().processPramatter(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be processed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }

        this.broker = null;
        this.createOrUpdate = false;
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        this.createOrUpdate = true;
        this.broker = broker;

        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()))) {
            return;
        }

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager()
                .processPramatterBeforeSave(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be processed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        //check saving list
        if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()))) {
            return;
        }

    	checkForUpdates(broker, document.getURI(), document);

        final XmldbURI uri = document.getCollection().getURI();
        if (uri.startsWith(SecurityManager.SECURITY_COLLECTION_URI)) {
            try {
                broker.getBrokerPool().getSecurityManager().processPramatter(broker, document);
            } catch (final ConfigurationException e) {
                LOG.error("Configuration can't be processed [" + document.getURI() + "]", e);
                //TODO : raise exception ? -pb
            }
        }

        this.broker = null;
        this.createOrUpdate = false;
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        checkForUpdates(broker, document.getURI(), document);
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        checkForUpdates(broker, document.getURI(), document);
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
        final Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
        if (conf != null) {
            Configurator.unregister(conf);
            //XXX: inform object that configuration was deleted
        }
    }

	@Override
    public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) {
	}

	@Override
	public void configure(final DBBroker broker, final Collection parent,
			final Map<String, List<? extends Object>> parameters)
			throws TriggerException {
	}

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname, final Attributes attributes) throws SAXException {
        if(createOrUpdate && namespaceURI != null && namespaceURI.equals(Configuration.NS) && ( (localName.equals(PrincipalType.ACCOUNT.getElementName()) && attributes.getValue("id") != null )|| (localName.equals(PrincipalType.GROUP.getElementName()) && !processingAccount))) {
            processingAccount = localName.equals(PrincipalType.ACCOUNT.getElementName()); //set group account/group guard
            defer(true);
        }
        super.startElement(namespaceURI, localName, qname, attributes);
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {

        super.endElement(namespaceURI, localName, qname);

        if(createOrUpdate && namespaceURI != null && namespaceURI.equals(Configuration.NS) && (localName.equals(PrincipalType.ACCOUNT.getElementName()) || (localName.equals(PrincipalType.GROUP.getElementName()) && !processingAccount))) {

            //we have now captured the entire account or group in our deferred queue,
            //so we can now process it in it's entirety
            if(processingAccount) {
                processPrincipal(PrincipalType.fromElementName(localName));
            }

            //stop deferring events and apply
            defer(false);

            if(localName.equals(PrincipalType.ACCOUNT.getElementName())) {
                //we are no longer processing an account
                processingAccount = false; //reset account/group guard
            }
        }
    }

    /**
     * When configuring a Principal (Account or Group) we need to
     * make sure of two things:
     *
     * 1) If the principal uses an old style id, i.e. before ACL Permissions
     * were introduced then we have to modernise this id
     *
     * 2) If the principal uses a name or id which already exists in
     * the database then we must avoid conflicts
     */
    private final void processPrincipal(final PrincipalType principalType) throws SAXException {
        final SAXEvent firstEvent = deferred.peek();
        if(!(firstEvent instanceof StartElement)) {
            throw new SAXException("Unbalanced SAX Events");
        }

        final StartElement start = ((StartElement)firstEvent);
        if(start.namespaceURI == null || !start.namespaceURI.equals(Configuration.NS) || !start.localName.equals(principalType.getElementName())) {
            throw new SAXException("First element does not match ending '" + principalType.getElementName() + "' element");
        }

        //if needed, update old style id to new style id
        final AttributesImpl attrs = new AttributesImpl(migrateIdAttribute(start.attributes, principalType));

        //check if there is a name collision, i.e. another principal with the same name
        final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
        final String principalName = findName();
        // first check if the account or group exists before trying to retrieve it
        // otherwise the LDAP realm will create a new user, leading to an endless loop
        final boolean principalExists = principalName != null && principalType.hasPrincipal(sm, principalName);
        Principal existingPrincipleByName = null;
        if (principalExists) {
            existingPrincipleByName = principalType.getPrincipal(sm, principalName);
        }

        final int newId;
        if(existingPrincipleByName != null) {
            //use id of existing principal which has the same name
            newId = existingPrincipleByName.getId();
        } else {

            //check if there is an id collision, i.e. another principal with the same id
            final Integer id = Integer.valueOf(attrs.getValue(ID_ATTR));
            final boolean principalIdExists = principalType.hasPrincipal(sm, id);
            Principal existingPrincipalById = null;
            if (principalIdExists) {
                existingPrincipalById = principalType.getPrincipal(sm, id);
            }

            if(existingPrincipalById != null) {

                //pre-allocate a new id, so as not to collide with the existing principal
                if(isValidating()) {
                    try {
                        principalType.preAllocateId(sm, preAllocatedId);
                    } catch(final PermissionDeniedException | EXistException e) {
                        throw new SAXException("Unable to pre-allocate principle id for " + principalType.getElementName() + ": " + principalName, e);
                    }
                }

                newId = preAllocatedId.getId();

                if(!isValidating()) {
                    preAllocatedId.clear();
                }
            } else {
                newId = id; //use the provided id as it is currently unallocated
            }
        }

        //update attributes of the principal in deferred
        attrs.setValue(attrs.getIndex(ID_ATTR), String.valueOf(newId));
        final StartElement prevPrincipalStart = (StartElement)deferred.poll();
        deferred.addFirst(new StartElement(prevPrincipalStart.namespaceURI, prevPrincipalStart.localName, prevPrincipalStart.qname, attrs));
    }

    /**
     * Migrates the id of a principal
     *
     * @param attrs The existing attributes of the principal
     * @param principalType The type of the principal
     *
     * @return The updated attributes containing the new id
     */
    private Attributes migrateIdAttribute(final Attributes attrs, final PrincipalType principalType) {
        final boolean aclPermissionInUse =
            PermissionFactory.getDefaultResourcePermission() instanceof ACLPermission;

        final Attributes newAttrs;
        final String strId = attrs.getValue(ID_ATTR);
        if (aclPermissionInUse && strId != null) {
            final Integer id = Integer.parseInt(strId);
            final Integer newId = principalType.migrateId(id);
            if(newId != null) {
                newAttrs = new AttributesImpl(attrs);
                ((AttributesImpl)newAttrs).setValue(newAttrs.getIndex(ID_ATTR), newId.toString());
            } else {
                newAttrs = attrs;
            }
        } else {
            newAttrs = attrs;
        }

        return newAttrs;
    }

    /**
     * Attempts to find and extract the text value
     * of the name element from the deferred elements
     *
     * @return The text value of the name element, or null otherwise
     */
    private String findName() {
        boolean inName = false;
        final StringBuilder name = new StringBuilder();
        for(final Iterator<SAXEvent> iterator = deferred.iterator(); iterator.hasNext(); ) {
            final SAXEvent event = iterator.next();
            if(event instanceof Element) {
                final Element element = (Element)event;
                if(element.namespaceURI != null && element.namespaceURI.equals(Configuration.NS) && element.localName.equals("name")) {
                    inName = !inName;
                }
            }

            if(inName && event instanceof Characters) {
                name.append(((Characters)event).ch);
            }
        }

        if(name.length() > 0) {
            return name.toString().trim();
        } else {
            return null;
        }
    }

    /**
     * Abstracts the difference between working
     * with Accounts or Groups
     */
    private enum PrincipalType {
        ACCOUNT("account", new HashMap<Integer, Integer>() {
            {
                put(-1, RealmImpl.UNKNOWN_ACCOUNT_ID);
                put(0, RealmImpl.SYSTEM_ACCOUNT_ID);
                put(1, RealmImpl.ADMIN_ACCOUNT_ID);
                put(2, RealmImpl.GUEST_ACCOUNT_ID);
            }
        }),
        GROUP("group", new HashMap<Integer, Integer>() {
            {
                put(-1, RealmImpl.UNKNOWN_GROUP_ID);
                put(1, RealmImpl.DBA_GROUP_ID);
                put(2, RealmImpl.GUEST_GROUP_ID);
            }
        });

        private final String elementName;
        private final Map<Integer, Integer> idMigration;

        PrincipalType(final String elementName, final Map<Integer, Integer> idMigration) {
            this.elementName = elementName;
            this.idMigration = idMigration;
        }

        /**
         * Get the local-name of the element
         * for the principal
         *
         * @return The local-name of the element used
         * in the persisted XML document for the principal
         */
        public String getElementName() {
            return elementName;
        }

        /**
         * Looks up a new Id given an old Id.
         *
         * Old Id's were used prior to the introduction of
         * ACL Permissions into eXist. Looking up
         * a non-old id will return null;
         *
         * @param oldId The older id
         *
         * @return The new Id or null if there is no mapping from old to new
         */
        public Integer migrateId(final Integer oldId) {
            return idMigration.get(oldId);
        }

        /**
         * Gets a principal of this type from the SecurityManager by name
         *
         * @param sm An instance of the SecurityManager
         * @param name The name of the principal
         *
         * @return A principal of this type, or null if there is no principal
         * matching the provided name
         */
        public Principal getPrincipal(final SecurityManager sm, final String name) {
            switch(this) {
                case ACCOUNT:
                    return sm.getAccount(name);
                case GROUP:
                    return sm.getGroup(name);
            }
            return null;
        }

        /**
         * Check if a user or group already exists (by name)
         *
         * @param sm
         * @param name
         * @return
         */
        public boolean hasPrincipal(final SecurityManager sm, final String name) {
            switch (this) {
                case ACCOUNT:
                    return sm.hasAccount(name);
                case GROUP:
                    return sm.hasGroup(name);
            }
            return false;
        }

        /**
         * Gets a principal of this type from the SecurityManager by id
         *
         * @param sm An instance of the SecurityManager
         * @param id The id of the principal
         *
         * @return A principal of this type, or null if there is no principal
         * matching the provided id
         */
        public Principal getPrincipal(final SecurityManager sm, final int id) {
            switch(this) {
                case ACCOUNT:
                    return sm.getAccount(id);
                case GROUP:
                    return sm.getGroup(id);
            }
            return null;
        }

        /**
         * Check if a user or group already exists (by id)
         *
         * @param sm
         * @param id
         * @return
         */
        public boolean hasPrincipal(final SecurityManager sm, final int id) {
            switch(this) {
                case ACCOUNT:
                    return sm.hasUser(id);
                case GROUP:
                    return sm.hasGroup(id);
            }
            return false;
        }

        public void preAllocateId(final SecurityManager sm, final PreAllocatedIdReceiver receiver) throws PermissionDeniedException, EXistException {
            switch(this) {
                case ACCOUNT:
                    sm.preAllocateAccountId(receiver);
                case GROUP:
                    sm.preAllocateGroupId(receiver);
            }
        }

        /**
         * Get the PrincipalType by its element name
         *
         * @return The PrincipalType for the element name
         *
         * @throws java.util.NoSuchElementException If there is no PrincipalType
         * for the provided element name
         */
        public static PrincipalType fromElementName(final String elementName) {
            for(final PrincipalType pt : PrincipalType.values()) {
                if(pt.getElementName().equals(elementName)) {
                    return pt;
                }
            }
            throw new NoSuchElementException("No PrincipalType with element name: " + elementName);
        }
    }

    private class PreAllocatedIdReceiver implements SecurityManager.PrincipalIdReceiver {
        Integer id = null;

        @Override
        public void allocate(final int id) {
            this.id = id;
        }

        public int getId() throws IllegalStateException {
            if(id == null) {
                throw new IllegalStateException("Id has not been allocated");
            } else {
                return id;
            }
        }

        public void clear() {
            this.id = null;
        }
    }
}
