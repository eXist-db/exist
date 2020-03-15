/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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

    public ACEAider(final ACE_ACCESS_TYPE accessType, final ACE_TARGET target, final String who, final int mode) {
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

    public ACEAider copy() {
        return new ACEAider(accessType, target, who, mode);
    }
}