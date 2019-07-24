/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.security;

public interface Account extends User {

    public final static int UNDEFINED_ID = -1;


    /**
     * Set the primary group of the user
     * If the user is not already in the group
     * they will also be added
     *
     * @param group The primary group
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setPrimaryGroup(Group group) throws PermissionDeniedException;
    
    public void assertCanModifyAccount(Account user) throws PermissionDeniedException;
    
    /**
     * Get the umask of the user
     *
     * @return The umask as an integer
     */
    public int getUserMask();
    
    /**
     * Set the umask of the user
     *
     * @param umask The umask as an integer
     */
    public void setUserMask(final int umask);

}