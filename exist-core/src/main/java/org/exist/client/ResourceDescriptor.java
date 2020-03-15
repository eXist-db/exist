/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import java.util.Date;

import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.xmldb.XmldbURI;

/**
 * Description of a resource, suitable for display by the graphical
 * client for instance.
 *
 * @author gpothier
 */
public abstract class ResourceDescriptor {
    private final XmldbURI name;
    protected final Permission permissions;
    private final Date date;
    
    public ResourceDescriptor(final XmldbURI name, final Permission permissions, final Date date) {
        this.name = name;
        this.permissions = permissions;
        this.date = date;
    }
    
    public XmldbURI getName() {
        return name;
    }
    
    public String getOwner() {
        return permissions.getOwner().getName();
    }

    public String getGroup() {
        return permissions.getGroup().getName();
    }
    
    public abstract String getPermissionsDescription();

    public Permission getPermissions() {
        return permissions;
    }
    
    public Date getDate() {
        return date;
    }
	
    public abstract boolean isCollection();
    
    public static class Document extends ResourceDescriptor {
        public Document(final XmldbURI name, final Permission permissions, final Date date) {
            super(name, permissions, date);
        }
        
        @Override
        public boolean isCollection() {
            return false;
        }

        @Override
        public String getPermissionsDescription() {
            return "-" + ((permissions instanceof ACLPermission && ((ACLPermission) permissions).getACECount() > 0) ? permissions.toString() + '+' : permissions.toString());
        }
    }
    
    public static class Collection extends ResourceDescriptor {
        public Collection(final XmldbURI name) {
            super(name, null, null);
        }
        
        public Collection(final XmldbURI name, final Permission permissions, final Date date) {
            super(name, permissions, date);
        }
        
        @Override
        public boolean isCollection() {
            return true;
        }

        @Override
        public String getPermissionsDescription() {
            return "c" + ((permissions instanceof ACLPermission && ((ACLPermission) permissions).getACECount() > 0) ? permissions.toString() + '+' : permissions.toString());
        }
    }
    
}