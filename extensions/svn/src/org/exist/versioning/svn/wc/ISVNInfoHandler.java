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

import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNInfoHandler</b> interface should be implemented in order to
 * be further provided to some of <b>SVNWCClient</b>'s doInfo() methods
 * to process information about Working Copy as well as remote (located in a 
 * repository) items. 
 * 
 * <p>
 * When running a info operation using an info handler an 
 * <b>SVNWCClient</b>'s doInfo() method generates an <b>SVNInfo</b>
 * object per each interesting item and dispatches that object to the
 * info handler where it's up to a developer to retrieve detailes   
 * from the <b>SVNInfo</b> object and interprete them in a desired way.
 * <p>
 * All calls to a <b>handleInfo()</b> method are synchronous - that is the
 * caller is blocked till the method finishes.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     SVNWCClient
 * @see     SVNInfo
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 * 
 */
public interface ISVNInfoHandler {
    /**
     * Handles item's information using an <b>SVNInfo</b> object.
     * 
     * @param info an object that contain's item's information details
     * @throws SVNException
     */
    public void handleInfo(SVNInfo info) throws SVNException;

}
