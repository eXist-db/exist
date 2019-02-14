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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author  Adam Retter <adam@exist-db.org>
 */
class CollectionDeferredPermission extends AbstractDeferredPermission<Collection> {
    
    private final static Logger LOG = LogManager.getLogger(CollectionDeferredPermission.class);
    
    public CollectionDeferredPermission(final RestoreListener listener, final Collection collection, final String owner, final String group, final Integer mode) {
        super(listener, collection, owner, group, mode);
    }

    @Override
    public void apply() {
        try {

            final UserManagementService service;
            if(getTarget().getName().equals(XmldbURI.ROOT_COLLECTION)) {
                service = (UserManagementService)getTarget().getService("UserManagementService", "1.0");
            } else {
                final Collection parent = getTarget().getParentCollection();
                service = (UserManagementService)parent.getService("UserManagementService", "1.0");
            }

            service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
        } catch (final XMLDBException xe) {
            String name = "unknown";
            try {
                name = getTarget().getName();
            } catch(final XMLDBException x) {
                LOG.error(x.getMessage(), x);
            }
            final String msg = "ERROR: Failed to set permissions on Collection '" + name + "'.";
            LOG.error(msg, xe);
            getListener().warn(msg);
        }
    }
}