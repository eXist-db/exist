/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2011 The eXist Project
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
 *  $Id: AbstractGroup.java 12846 2010-10-01 05:23:29Z shabanovd $
 */

package org.exist.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.Reference;
import org.exist.config.ReferenceImpl;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationReferenceBy;
import org.exist.security.internal.GroupImpl;
import org.exist.storage.DBBroker;

@ConfigurationClass("")
public abstract class AbstractGroup extends AbstractPrincipal implements Comparable<Object>, Group {

    private final static Logger LOG = LogManager.getLogger(AbstractGroup.class);
    
    @ConfigurationFieldAsElement("manager")
    @ConfigurationReferenceBy("name")
    private List<Reference<SecurityManager, Account>> managers = new ArrayList<Reference<SecurityManager, Account>>();
    
    @ConfigurationFieldAsElement("metadata")
    private Map<String, String> metadata = new HashMap<String, String>();

    public AbstractGroup(final DBBroker broker, final AbstractRealm realm, final int id, final String name, final List<Account> managers) throws ConfigurationException {
        super(broker, realm, realm.collectionGroups, id, name);
        
        if(managers != null) {
            for(final Account manager : managers) {
                _addManager(manager);
            }
        }
    }

    public AbstractGroup(final DBBroker broker, final AbstractRealm realm, final String name) throws ConfigurationException {
        super(broker, realm, realm.collectionGroups, UNDEFINED_ID, name);
    }

    public AbstractGroup(final AbstractRealm realm, final Configuration configuration) throws ConfigurationException {
        super(realm, configuration);

        //it require, because class's fields initializing after super constructor
        if (this.configuration != null) {
                this.configuration = Configurator.configure(this, this.configuration);
        }
    }

    @Override
    public int compareTo(final Object other) {
        if(!(other instanceof GroupImpl)) {
            throw new IllegalArgumentException("wrong type");
        }
        return name.compareTo(((GroupImpl)other).name);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("<group name=\"");
        buf.append(name);
        buf.append("\" id=\"");
        buf.append(Integer.toString(id));
        buf.append("\">");
        try {
            for(final Account manager : getManagers()) {
                buf.append("<manager name=\"").append(manager.getUsername()).append("\"/>");
            }
        } catch(final Throwable e) {
            e.printStackTrace();
            buf.append("<manager error=\"").append(e.getMessage()).append("\"/>");
        }
        buf.append("</group>");
        
        return buf.toString();
    }

    @Override
    public void assertCanModifyGroup(final Account user) throws PermissionDeniedException {
        if(user == null) {
            throw new PermissionDeniedException("Unspecified Account is not allowed to modify group '" + getName() + "'");
        } else if(!user.hasDbaRole() && !isManager(user)) {
            throw new PermissionDeniedException("Account '" + user.getName() + "' is not allowed to modify group '" + getName() + "'");
        }
    }

    @Override
    public boolean isManager(final Account account) {
    	for(final Reference<SecurityManager, Account> manager : managers) {
            final Account acc = manager.resolve();
            if(acc != null && acc.equals(account)) {
                return true;
            }
    	}
    	return false;
    }

    protected void _addManager(final Account account) {
        //check the manager is not already present
        for(final Reference<SecurityManager, Account> manager : managers) {
            final String refName = manager.getName();
            if(refName != null && refName.equals(account.getName())) {
                return;
            }
        }

        //add the manager
        managers.add(
            new ReferenceImpl<SecurityManager, Account>(
                getRealm().getSecurityManager(),
                account,
                account.getName()
            )
        );
    }

    @Override
    public void addManager(final Account manager) throws PermissionDeniedException {
    	final Subject subject = getDatabase().getActiveBroker().getCurrentSubject();
        assertCanModifyGroup(subject);
        
        _addManager(manager);
    }

    @Override
    public void addManagers(final List<Account> managers) throws PermissionDeniedException {
    	if(managers != null) {
            for(final Account manager : managers) {
                addManager(manager);
            }
        }
    }
    
    public void addManager(final String name) throws PermissionDeniedException {
        final Subject subject = getDatabase().getActiveBroker().getCurrentSubject();
        assertCanModifyGroup(subject);
        
        //check the manager is not already present`
        for(final Reference<SecurityManager, Account> ref : managers) {
            final String refName = ref.getName();
            if(refName != null && refName.equals(name)) {
                return;
            }
        }

        managers.add(new ReferenceImpl<SecurityManager, Account>(getRealm().getSecurityManager(), "getAccount", name));
    }

    @Override
    public List<Account> getManagers() {
    	
        //we use a HashSet to ensure a unique set of managers
        //under some cases it is possible for the same manager to
        //appear twice in a group config file, but we only want
        //to know about them once!
    	final Set<Account> set = new HashSet<Account>();
    	
        if(managers != null) {
            for(final Reference<SecurityManager, Account> ref : managers) {
                final Account acc = ref.resolve();
                if(acc != null) {
                    set.add(acc);
                } else {
                    LOG.warn("Unable to resolve reference to group manager '" + ref.getName() + "' for group '" + getName() + "'");
                }
            }
        }
    	
        return new ArrayList<Account>(set);
    }

    @Override
    public void removeManager(final Account account) throws PermissionDeniedException {

        final Subject subject = getDatabase().getActiveBroker().getCurrentSubject();
        assertCanModifyGroup(subject);

        for(final Reference<SecurityManager, Account> ref : managers) {
            final Account acc = ref.resolve();
            if(acc.getName().equals(account.getName())) {
                managers.remove(ref);
                break;
            }
        }
    }
    
    //this method used only at tests, don't use it other places
    public void setManagers(final List<Reference<SecurityManager, Account>> managers) {
        this.managers = managers;
    }
    
    @Override
    public String getMetadataValue(final SchemaType schemaType) {
        return metadata.get(schemaType.getNamespace());
    }

    @Override
    public void setMetadataValue(final SchemaType schemaType, final String value) {
        metadata.put(schemaType.getNamespace(), value);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        final Set<SchemaType> metadataKeys = new HashSet<SchemaType>();
        
        for(final String key : metadata.keySet()) {
            //XXX: other types?
            if(AXSchemaType.valueOfNamespace(key) != null) {
                metadataKeys.add(AXSchemaType.valueOfNamespace(key));
            } else if(EXistSchemaType.valueOfNamespace(key) != null){
                metadataKeys.add(EXistSchemaType.valueOfNamespace(key));
            }
        }
        return metadataKeys;
    }
    
    @Override
    public void clearMetadata() {
    	metadata.clear();
    }
}