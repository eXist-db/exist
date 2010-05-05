/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc.admin;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNVersionedProperties {
    private SVNProperties myProperties;
    private boolean myIsModified;
    
    protected SVNVersionedProperties(SVNProperties props) {
        myProperties = props;
        myIsModified = false;
    }
    
    public abstract boolean containsProperty(String name) throws SVNException;
    
    public abstract SVNPropertyValue getPropertyValue(String name) throws SVNException;

    public String getStringPropertyValue(String name) throws SVNException {
        SVNPropertyValue value = getPropertyValue(name);
        return value == null ? null : value.getString();
    }

    public boolean isModified() {
        return myIsModified;
    }
    
    protected void setModified(boolean modified) {
        myIsModified = modified;
    }
    
    public boolean isEmpty() throws SVNException {
        SVNProperties props = loadProperties();
        return props == null || props.isEmpty();
    }
    
    public Collection getPropertyNames(Collection target) throws SVNException {
        SVNProperties props = loadProperties();

        target = target == null ? new TreeSet() : target;
        if (isEmpty()) {
            return target;
        }
        for (Iterator names = props.nameSet().iterator(); names.hasNext();) {
            target.add(names.next());
        }
        return target;
    }

    public void setPropertyValue(String name, SVNPropertyValue value) throws SVNException {
        SVNProperties props = loadProperties();
        if (value != null) {
            props.put(name, value);
        } else {
            props.remove(name);
        }
        myIsModified = true;
    }

    public SVNVersionedProperties compareTo(SVNVersionedProperties properties) throws SVNException {
        SVNProperties theseProps = loadProperties(); 
        if (theseProps == null) {
            return wrap(new SVNProperties());
        }
        return wrap(theseProps.compareTo(properties.loadProperties()));
    }
    
    public void copyTo(SVNVersionedProperties destination) throws SVNException {
        SVNProperties props = loadProperties();
        if (isEmpty()) {
            destination.removeAll();
        } else {
            destination.put(props);
        }
    }
    
    public void removeAll() throws SVNException {
        SVNProperties props = loadProperties();
        if (!isEmpty()) {
            props.clear();
            myIsModified = true;
        }
    }
    
    public boolean equals(SVNVersionedProperties props) throws SVNException {
        return compareTo(props).isEmpty();
    }
    
    public SVNProperties asMap() throws SVNException {
        return loadProperties() != null ? new SVNProperties(loadProperties()) : new SVNProperties();
    }
    
    protected void put(SVNProperties props) throws SVNException {
        SVNProperties thisProps = loadProperties(); 
        thisProps.clear();
        thisProps.putAll(props);
        myIsModified = true;
    }

    protected SVNProperties getProperties() {
        return myProperties;
    }
    
    protected void setPropertiesMap(SVNProperties props) {
        myProperties = props;
    }
    
    protected abstract SVNVersionedProperties wrap(SVNProperties properties);

    protected abstract SVNProperties loadProperties() throws SVNException;

}
