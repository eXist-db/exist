/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.wc.admin;


/**
 * <b>SVNSyncInfo</b> represents information on repository synchronization
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNSyncInfo {
    private String mySrcURL;
    private String mySourceRepositoryUUID;
    private long myLastMergedRevision;

    /**
     * Creates a new <code>SVNSyncInfo</code> object.
     * 
     * @param srcURL                 url of the source repository to synchronize with
     * @param sourceRepositoryUUID   uuid of the source repository
     * @param lastMergedRevision     last source repository revision synchronized with 
     * @since 1.3
     */
    public SVNSyncInfo(String srcURL, String sourceRepositoryUUID, long lastMergedRevision) {
        mySrcURL = srcURL;
        mySourceRepositoryUUID = sourceRepositoryUUID;
        myLastMergedRevision = lastMergedRevision;
    }

    /**
     * Returns the url of the source repository.
     * 
     * @return url of the source repository synchronized with 
     * @since  1.3
     */
    public String getSrcURL() {
        return mySrcURL;
    }
    
    /**
     * Returns the source repository UUID.
     * @return  source repository UUID
     * @since  1.3
     */
    public String getSourceRepositoryUUID() {
        return mySourceRepositoryUUID;
    }
    
    /**
     * Returns the last revision of the source repository 
     * synchronized with.
     * @return last merged revision
     * @since  1.3
     */
    public long getLastMergedRevision() {
        return myLastMergedRevision;
    }
    
}
