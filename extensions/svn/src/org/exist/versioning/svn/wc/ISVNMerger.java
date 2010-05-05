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

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;


/**
 * <b>ISVNMerger</b> is the merge driver interface used by <code>SVNKit</code> in merging operations. 
 * 
 * <p>
 * Merge drivers are created by a merger factory implementing the 
 * {@link ISVNMergerFactory} interface. Read more about that interface to
 * find out how to get a default implementation of <code>ISVNMerger</code>.  
 * 
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 */
public interface ISVNMerger {
    
    /**
     * Performs a text merge.
     * 
     * @param  files            files invoked in merge  
     * @param  dryRun           if <span class="javakeyword">true</span>, merge is simulated only, no real
     *                          changes are done
     * @param  options          merge options to take into account
     * @return                  result of merging 
     * @throws SVNException 
     */
    public SVNMergeResult mergeText(SVNMergeFileSet files, boolean dryRun, SVNDiffOptions options) throws SVNException;
   
    /**
     * Given <code>adminArea</code>/<code>localPath</code> and property changes (<code>propDiff</code>) based 
     * on <code>serverBaseProps</code>, merges the changes into the working copy.
     * 
     * @param  localPath           working copy path base name
     * @param  workingProperties   working properties
     * @param  baseProperties      pristine properties
     * @param  serverBaseProps     properties that come from the server
     * @param  propDiff            property changes that come from the repository
     * @param  adminArea           admin area object representing the <code>.svn<./code> admin area of 
     *                             the target which properties are merged
     * @param  log                 logger
     * @param  baseMerge           if <span class="javakeyword">false</span>, then changes only working properties;
     *                             otherwise, changes both the base and working properties
     * @param  dryRun              if <span class="javakeyword">true</span>, merge is simulated only, no real
     *                             changes are done
     * @return                     result of merging 
     * @throws SVNException 
     */
	public SVNMergeResult mergeProperties(String localPath, SVNProperties workingProperties, 
			SVNProperties baseProperties, SVNProperties serverBaseProps, SVNProperties propDiff,
			SVNAdminArea adminArea, SVNLog log, boolean baseMerge, boolean dryRun) throws SVNException;

}
