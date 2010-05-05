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
package org.exist.versioning.svn.internal.wc;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNRevisionStatus {
	private long myMinRevision;
	private long myMaxRevision;
	private boolean myIsSwitched;
	private boolean myIsModified;
    private boolean myIsSparseCheckout;
	
	public SVNRevisionStatus(long minRevision, long maxRevision,
			boolean isSwitched, boolean isModified, boolean isSparseCheckout) {
		myMinRevision = minRevision;
		myMaxRevision = maxRevision;
		myIsSwitched = isSwitched;
		myIsModified = isModified;
		myIsSparseCheckout = isSparseCheckout;
	}

	public long getMinRevision() {
		return myMinRevision;
	}

	public long getMaxRevision() {
		return myMaxRevision;
	}

	public boolean isSwitched() {
		return myIsSwitched;
	}

	public boolean isModified() {
		return myIsModified;
	}

    public boolean isSparseCheckout() {
        return myIsSparseCheckout;
    }
	
}
