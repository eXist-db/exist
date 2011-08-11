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

import java.util.ArrayList;
import java.util.List;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.internal.aider.ACEAider;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
abstract class AbstractDeferredPermission<T> implements DeferredPermission {

    final private RestoreListener listener;
    final private T target;
    final private String owner;
    final private String group;
    final private int mode;
    final List<ACEAider> aces = new ArrayList<ACEAider>();

    public AbstractDeferredPermission(RestoreListener listener, T target, String owner, String group, int mode) {
        this.listener = listener;
        this.target = target;
        this.owner = owner;
        this.group = group;
        this.mode = mode;
    }
    
    protected RestoreListener getListener() {
        return listener;
    }

    protected T getTarget() {
        return target;
    }

    protected List<ACEAider> getAces() {
        return aces;
    }

    protected String getGroup() {
        return group;
    }

    protected int getMode() {
        return mode;
    }

    protected String getOwner() {
        return owner;
    }

    @Override
    public void addACE(int index, ACE_TARGET target, String who, ACE_ACCESS_TYPE access_type, int mode) {
        aces.add(new ACEAider(access_type, target, who, mode));
    }
}