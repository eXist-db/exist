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
 *  $Id: UnixStylePermission.java 14502 2011-05-23 10:12:51Z deliriumsky $
 */
package org.exist.security.internal.aider;

import java.io.Serializable;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class ACEAider implements Serializable {
    private ACE_ACCESS_TYPE accessType;
    private ACE_TARGET target;
    private String who;
    private int mode;

    public ACEAider() {
    }

    public ACEAider(ACE_ACCESS_TYPE accessType, ACE_TARGET target, String who, int mode) {
        this.accessType = accessType;
        this.target = target;
        this.who = who;
        this.mode = mode;
    }

    public ACE_ACCESS_TYPE getAccessType() {
        return accessType;
    }

    public int getMode() {
        return mode;
    }

    public ACE_TARGET getTarget() {
        return target;
    }

    public String getWho() {
        return who;
    }

    public void setAccessType(ACE_ACCESS_TYPE accessType) {
        this.accessType = accessType;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setTarget(ACE_TARGET target) {
        this.target = target;
    }

    public void setWho(String who) {
        this.who = who;
    }
}