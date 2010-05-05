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
public class SVNDiffConflictChoiceStyle {
    public static final SVNDiffConflictChoiceStyle CHOOSE_MODIFIED_LATEST = new SVNDiffConflictChoiceStyle();
    public static final SVNDiffConflictChoiceStyle CHOOSE_RESOLVED_MODIFIED_LATEST = new SVNDiffConflictChoiceStyle();
    public static final SVNDiffConflictChoiceStyle CHOOSE_MODIFIED_ORIGINAL_LATEST = new SVNDiffConflictChoiceStyle();
    public static final SVNDiffConflictChoiceStyle CHOOSE_MODIFIED = new SVNDiffConflictChoiceStyle();
    public static final SVNDiffConflictChoiceStyle CHOOSE_LATEST = new SVNDiffConflictChoiceStyle();
    public static final SVNDiffConflictChoiceStyle CHOOSE_ONLY_CONFLICTS = new SVNDiffConflictChoiceStyle();
    
    private SVNDiffConflictChoiceStyle() {
    }
}
