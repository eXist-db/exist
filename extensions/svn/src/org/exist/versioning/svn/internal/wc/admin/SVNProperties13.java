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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNProperties13 extends SVNVersionedProperties {

    public SVNProperties13(SVNProperties properties) {
        super(properties);
    }

    public boolean containsProperty(String name) throws SVNException {
        if (!isEmpty()) {
            SVNProperties props = loadProperties();
            return props.containsName(name);
        }
        return false;
    }

    public SVNPropertyValue getPropertyValue(String name) throws SVNException {
        if (getProperties() != null && getProperties().containsName(name)) {
            return getProperties().getSVNPropertyValue(name);
        }
        if (!isEmpty()) {
            SVNProperties props = loadProperties();
            return props.getSVNPropertyValue(name); 
        }
        return null;
    }

    protected SVNProperties loadProperties() throws SVNException {
        SVNProperties props = getProperties();
        if (props == null) {
            props = new SVNProperties();
            setPropertiesMap(props);
        }
        return props;
    }

    protected SVNVersionedProperties wrap(SVNProperties properties) {
        return new SVNProperties13(properties);
    }
}
