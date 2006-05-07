/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
    private XmldbURI name;
    private String owner;
    private String group;
    private String permissions;
    private Date date;
    
    public ResourceDescriptor(XmldbURI aName, String aOwner, 
                              String aGroup, String aPermissions, Date date ) {
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
        public Document(XmldbURI aName, String aOwner, String aGroup, String aPermissions, Date date) {
            super(aName, aOwner, aGroup, aPermissions, date);
        }
        
        public boolean isCollection() {
            return false;
        }
    }
    
    public static class Collection extends ResourceDescriptor {
        public Collection(XmldbURI aName) {
            super(aName, null, null, null, null);
        }
        
        public Collection(XmldbURI aName, String aOwner, String aGroup, String aPermissions, Date date) {
            super(aName, aOwner, aGroup, aPermissions, date);
        }
        
        public boolean isCollection() {
            return true;
        }
    }
    
}