/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2011 The eXist-db Project
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
 *  $Id: Restore.java 15109 2011-08-09 13:03:09Z deliriumsky $
 */
package org.exist.backup.restore;

import org.apache.log4j.Logger;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * 
 * @author Adam Retter <adam@exist-db.org>
 */

class ResourceDeferredPermission extends AbstractDeferredPermission<Resource> {

    private final static Logger LOG = Logger.getLogger(ResourceDeferredPermission.class);
    
    public ResourceDeferredPermission(RestoreListener listener, Resource resource, String owner, String group, Integer mode) {
        super(listener, resource, owner, group, mode);
    }

    @Override
    public void apply() {
        try {
            final UserManagementService service = (UserManagementService)getTarget().getParentCollection().getService("UserManagementService", "1.0");
            final Permission permissions = service.getPermissions(getTarget());
            service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
        } catch(final XMLDBException xe) {
            String name = "unknown";
            try { name = getTarget().getId(); } catch(final XMLDBException x) { LOG.error(x.getMessage(), x); }
            final String msg = "ERROR: Failed to set permissions on Document '" + name + "'.";
            LOG.error(msg, xe);
            getListener().warn(msg);
        }
    }
}
