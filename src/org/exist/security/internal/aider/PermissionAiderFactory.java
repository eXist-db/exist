/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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

import org.apache.log4j.Logger;

/**
 * Instantiates an appropriate Permission class based on the current configuration
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class PermissionAiderFactory {

    private final static Logger LOG = Logger.getLogger(PermissionAiderFactory.class);

    public static PermissionAider getPermission(String ownerName, String groupName, int mode) {
        PermissionAider permission = null;
        try {
            permission = new SimpleACLPermissionAider(ownerName, groupName, mode);
	} catch(final Throwable ex) {
          LOG.error("Exception while instantiating security permission class.", ex);
        }
        return permission;
    }
}