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


/**
 * The <b>ISVNMergerFactory</b> interface should be implemented in
 * order to be used as a factory of merger drivers intended for 
 * merging operations.
 * 
 * <p>
 * To get a default merger factory implementation call the 
 * {@link ISVNOptions#getMergerFactory()} method of a default run-time
 * configuration driver. How to obtain a default run-time configuration
 * driver read more on {@link ISVNOptions} and {@link SVNWCUtil}.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNMerger
 */
public interface ISVNMergerFactory {
    /**
     * Creates a new merger driver.
     * 
     * <p>
     * If a merger driver can not cleanly apply delta to a file (in case of a conflict state) 
     * then for each conflicting contents fragment the driver puts local data between the 
     * specified <code>conflictStart</code> and <code>conflictSeparator</code> bytes, and the newcomer
     * data between the specified <code>conflictSeparator</code> and <code>conflictEnd</code> ones. 
     * And all these bytes are then written to the file in the place of a conflict.
     * 
     * @param  conflictStart      bytes that come in the very beginning of a conflict
     * @param  conflictSeparator  bytes that are used to separate two conflicting fragments - 
     *                            local data and the newcomer one  
     * @param  conflictEnd        bytes that come in the very end of a conflict  
     * @return                    a merger driver
     */
    public ISVNMerger createMerger(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd);
    
}
