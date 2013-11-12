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
package org.exist.client;

import java.util.Date;

import org.exist.xmldb.XmldbURI;

/**
 * Description of a resource, suitable for display by the graphical
 * client for instance.
 *
 * @author gpothier
 */
public abstract class ResourceDescriptor {
    private final XmldbURI name;
    private final String owner;
    private final String group;
    private final String permissions;
    private final Date date;
    
    public ResourceDescriptor(final XmldbURI aName, final String aOwner,
            final String aGroup, final String aPermissions, final Date date ) {
        name = aName;
        owner = aOwner;
        group = aGroup;
        permissions = aPermissions;
        this.date = date;
    }
    
    public String getGroup() {
        return group;
    }
    
    public XmldbURI getName() {
        return name;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public String getPermissions() {
        return permissions;
    }
    
    public Date getDate() {
        return date;
    }
	
    public abstract boolean isCollection();
    
    public static class Document extends ResourceDescriptor {
        public Document(final XmldbURI aName, final String aOwner,
                final String aGroup, final String aPermissions, final Date date) {
            super(aName, aOwner, aGroup, aPermissions, date);
        }
        
        @Override
        public boolean isCollection() {
            return false;
        }
    }
    
    public static class Collection extends ResourceDescriptor {
        public Collection(final XmldbURI aName) {
            super(aName, null, null, null, null);
        }
        
        public Collection(final XmldbURI aName, final String aOwner,
                final String aGroup, final String aPermissions, final Date date) {
            super(aName, aOwner, aGroup, aPermissions, date);
        }
        
        @Override
        public boolean isCollection() {
            return true;
        }
    }
    
}