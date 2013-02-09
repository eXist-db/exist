/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.security.internal.aider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.exist.config.Configuration;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.internal.RealmImpl;
import org.exist.security.realm.Realm;
import org.exist.storage.DBBroker;

/**
 * Group details.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class GroupAider implements Group {

	private final String realmId;
	private final String name;
	private final int id;
	
	private final List<Account> managers = new ArrayList<Account>();
        private final Map<SchemaType, String> metadataValues = new HashMap<SchemaType, String>();
	
	public GroupAider(int id) {
            this(id, null, null);
	}
	
	public GroupAider(String realmId, String name) {
            this(UNDEFINED_ID, realmId, name);
	}

	public GroupAider(int id, String realmId, String name) {
            this.id = id;
            this.name = name;
            this.realmId = realmId;
	}
	
	public GroupAider(String name) {
            this(RealmImpl.ID, name);
	}

	/* (non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Group#getId()
	 */
	@Override
	public int getId() {
		return id;
	}

	@Override
	public boolean isConfigured() {
		return false;
	}

	@Override
	public Configuration getConfiguration() {
		return null;
	}

	@Override
	public String getRealmId() {
		return realmId;
	}

    @Override
    public void save() throws PermissionDeniedException {
        //do nothing
    }

    @Override
    public void save(DBBroker broker) throws PermissionDeniedException {
        //do nothing
    }
    
    @Override
    public boolean isManager(Account account) {
        for(final Account manager : managers) {
            if(manager.getName().equals(account.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addManager(Account account) throws PermissionDeniedException {
        if(!managers.contains(account)) {
            managers.add(account);
        }
    }

    @Override
    public void addManagers(List<Account> managers) throws PermissionDeniedException {
    	for (final Account manager : managers) {
    		addManager(manager);
    	}
    }

    @Override
    public List<Account> getManagers() throws PermissionDeniedException {
        return managers;
    }

    @Override
    public void removeManager(Account account) throws PermissionDeniedException {
        for(final Account manager : managers) {
            if(manager.getName().equals(account.getName())) {
                managers.remove(manager);
                break;
            }
        }
    }

    @Override
    public void assertCanModifyGroup(Account account) throws PermissionDeniedException {
        //do nothing
        //TODO do we need to check any permissions?
    }



    @Override
    public Realm getRealm() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setMetadataValue(final SchemaType schemaType, final String value) {
        metadataValues.put(schemaType, value);
    }

    @Override
    public String getMetadataValue(final SchemaType schemaType) {
        return metadataValues.get(schemaType);
    }

    @Override
    public Set<SchemaType> getMetadataKeys() {
        return metadataValues.keySet();
    }

    @Override
    public void clearMetadata() {
        metadataValues.clear();
    }
    
    
}