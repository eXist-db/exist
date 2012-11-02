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
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.Reference;
import org.exist.config.ReferenceImpl;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationReferenceBy;
import org.exist.security.internal.GroupImpl;

@ConfigurationClass("")
public abstract class AbstractGroup extends AbstractPrincipal implements Comparable<Object>, Group {

    @ConfigurationFieldAsElement("manager")
    @ConfigurationReferenceBy("name")
    private List<Reference<SecurityManager, Account>> managers = new ArrayList<Reference<SecurityManager, Account>>();
    
    @ConfigurationFieldAsElement("metadata")
    private Map<String, String> metadata = new HashMap<String, String>();

    public AbstractGroup(AbstractRealm realm, int id, String name, List<Account> managers) throws ConfigurationException {
        super(realm, realm.collectionGroups, id, name);
        
        if(managers != null) {
            for(Account manager : managers) {
                _addManager(manager);
            }
        }
    }

    public AbstractGroup(AbstractRealm realm, String name) throws ConfigurationException {
        super(realm, realm.collectionGroups, UNDEFINED_ID, name);
    }

    public AbstractGroup(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);

        //it require, because class's fields initializing after super constructor
        if (this.configuration != null) {
                this.configuration = Configurator.configure(this, this.configuration);
        }
    }

    @Override
    public int compareTo(Object other) {
        if(!(other instanceof GroupImpl)) {
            throw new IllegalArgumentException("wrong type");
        }
        return name.compareTo(((GroupImpl)other).name);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("<group name=\"");
        buf.append(name);
        buf.append("\" id=\"");
        buf.append(Integer.toString(id));
        buf.append("\">");
        try {
	        for(Account manager : getManagers()) {
	            buf.append("<manager name=\"" + manager.getUsername() + "\"/>");
	        }
        } catch (Throwable e) {
            buf.append("<manager error=\"" + e.getMessage() + "\"/>");
		}
        buf.append("</group>");
        return buf.toString();
    }

    @Override
    public void assertCanModifyGroup(Account user) throws PermissionDeniedException {
        if(user == null) {
            throw new PermissionDeniedException("Unspecified Account is not allowed to modify group '" + getName() + "'");
        } else if(!user.hasDbaRole() && !isManager(user)) {
            throw new PermissionDeniedException("Account '" + user.getName() + "' is not allowed to modify group '" + getName() + "'");
        }
    }

    @Override
    public boolean isManager(Account account) {
    	for (Reference<SecurityManager, Account> manager : managers) {
    		Account acc = manager.resolve();
    		if (acc != null && acc.equals(account))
    			return true;
    	}
    	return false;
    }

    protected void _addManager(Account account) {
        if(!managers.contains(account.getName())) {
            managers.add(
        		new ReferenceImpl<SecurityManager, Account>(
    				getRealm().getSecurityManager(),
    				account
        		)
        	);
        }
    }

    @Override
    public void addManager(Account manager) throws PermissionDeniedException {
    	Subject subject = getDatabase().getSubject();

        assertCanModifyGroup(subject);
        
        _addManager(manager);
    }

    @Override
    public void addManagers(List<Account> managers) throws PermissionDeniedException {
    	if (managers != null)
	    	for (Account manager : managers) {
	    		addManager(manager);
	    	}
    }
    
    public void addManager(String name) throws PermissionDeniedException {
    	Subject subject = getDatabase().getSubject();

        assertCanModifyGroup(subject);
        
        for(Reference<SecurityManager, Account> ref : managers) {
            if(ref.resolve().getName().equals(name)) {
                return;
            }
        }

        managers.add(
    		new ReferenceImpl<SecurityManager, Account>(
				getRealm().getSecurityManager(),
				"getAccount",
				name
    		)
    	);
    }

    @Override
    public List<Account> getManagers() {
    	
    	List<Account> list = new ArrayList<Account>(managers.size());
    	
    	for (Reference<SecurityManager, Account> ref : managers) {
    		Account acc = ref.resolve();
    		if (acc != null)
    			list.add(acc);
    	}
        
    	return list;
    }

    @Override
    public void removeManager(Account account) throws PermissionDeniedException {

        Account subject = getDatabase().getSubject();

        assertCanModifyGroup(subject);

        for(Reference<SecurityManager, Account> ref : managers) {
        	Account acc = ref.resolve();
            if(acc.getName().equals(account.getName())) {
                managers.remove(ref);
                break;
            }
        }
    }
    
    //this method used only at tests, don't use it other places
    protected void setManagers(List<Reference<SecurityManager, Account>> managers) {
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
    
    public void clearMetadata() {
    	metadata.clear();
    }
}