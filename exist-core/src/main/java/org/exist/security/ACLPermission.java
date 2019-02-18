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
package org.exist.security;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public interface ACLPermission {

    public static enum ACE_ACCESS_TYPE {
        DENIED(01),
        ALLOWED(02);
        private final int val;

        ACE_ACCESS_TYPE(int val) {
            this.val = val;
        }

        public int getVal() {
            return val;
        }

        public static ACE_ACCESS_TYPE fromVal(int val) {
            for(final ACE_ACCESS_TYPE access_type : ACE_ACCESS_TYPE.values()) {
                if(access_type.getVal() == val) {
                    return access_type;
                }
            }

            throw new RuntimeException("Unknown ACE Access Type");
        }
    }

    public static enum ACE_TARGET {
        USER(01),
        GROUP(02);
        private final int val;

        ACE_TARGET(int val) {
            this.val = val;
        }

        public int getVal() {
            return val;
        }

        public static ACE_TARGET fromVal(int val) {
            for(final ACE_TARGET target : ACE_TARGET.values()) {
                if(target.getVal() == val) {
                    return target;
                }
            }

            throw new RuntimeException("Unknown ACE Target");
        }
    }

    public short getVersion();

    public void addACE(ACE_ACCESS_TYPE access_type, ACE_TARGET target, String name, int mode) throws PermissionDeniedException;

    public int getACECount();

    public ACE_ACCESS_TYPE getACEAccessType(int index);

    public ACE_TARGET getACETarget(int index);
    
    /**
     * Convenience method for getting the name of the user or group
     * of which this ace is applied to
     */
    public String getACEWho(int index);
   
    public int getACEMode(int index);

    /**
     * Clears all ACE's
     */
    public void clear() throws PermissionDeniedException;
    
    public boolean isCurrentSubjectCanWriteACL();
}