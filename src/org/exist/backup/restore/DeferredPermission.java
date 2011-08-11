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
abstract class DeferredPermission<T> {

    final private RestoreListener listener;
    final private T target;
    final private String owner;
    final private String group;
    final private int mode;
    final List<ACEAider> aces = new ArrayList<ACEAider>();

    public DeferredPermission(RestoreListener listener, T target, String owner, String group, int mode) {
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

    protected void addACE(int index, ACE_TARGET target, String who, ACE_ACCESS_TYPE access_type, int mode) {
        aces.add(new ACEAider(access_type, target, who, mode));
    }

    public abstract void apply();
}