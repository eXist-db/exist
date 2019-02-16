/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.security;

import java.util.List;

public interface Group extends Principal {

    public final static int UNDEFINED_ID = -1;
    
    public boolean isManager(Account account);

    public void addManager(Account account) throws PermissionDeniedException;

    public void addManagers(List<Account> managers) throws PermissionDeniedException;

    public List<Account> getManagers() throws PermissionDeniedException;

    public void removeManager(Account account) throws PermissionDeniedException;

    public void assertCanModifyGroup(Account account) throws PermissionDeniedException;
}