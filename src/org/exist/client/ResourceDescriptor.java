/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

/**
 * Description of a resource, suitable for display by the graphical
 * client for instance.
 *
 * @author gpothier
 */
public abstract class ResourceDescriptor {
    private String name;
    private String owner;
    private String group;
    private String permissions;
    
    public ResourceDescriptor(String aName, String aOwner, 
                              String aGroup, String aPermissions) {
        name = aName;
        owner = aOwner;
        group = aGroup;
        permissions = aPermissions;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getName() {
        return name;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public String getPermissions() {
        return permissions;
    }
    
    public abstract boolean isCollection();
    
    public static class Document extends ResourceDescriptor {
        public Document(String aName, String aOwner, String aGroup, String aPermissions) {
            super(aName, aOwner, aGroup, aPermissions);
        }
        
        public boolean isCollection() {
            return false;
        }
    }
    
    public static class Collection extends ResourceDescriptor {
        public Collection(String aName) {
            super(aName, null, null, null);
        }
        
        public Collection(String aName, String aOwner, String aGroup, String aPermissions) {
            super(aName, aOwner, aGroup, aPermissions);
        }
        
        public boolean isCollection() {
            return true;
        }
    }
    
}