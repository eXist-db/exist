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
package org.exist.versioning.svn.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNPropertyValueProvider</b> interface should be implemented
 * to be further provided to {@link SVNWCClient#doSetProperty(java.io.File, ISVNPropertyValueProvider, boolean, org.tmatesoft.svn.core.SVNDepth, ISVNPropertyHandler, java.util.Collection)}
 * method for defining properties to change.
 *
 * @author TMate Software Ltd.
 * @version 1.3
 * @since   1.2
 * @see SVNWCClient
 */
public interface ISVNPropertyValueProvider {


    /**
     * Defines local item's properties to be installed.
     *
     * @param path          an WC item's path
     * @param properties    an item's versioned properties
     * @return              <b>SVNProperties</b> object which stores properties to be installed on an item
     * @throws SVNException
     */
    public SVNProperties providePropertyValues(File path, SVNProperties properties) throws SVNException;
}
