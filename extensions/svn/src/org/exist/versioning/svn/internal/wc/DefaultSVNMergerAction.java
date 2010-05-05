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
public class DefaultSVNMergerAction {

    public static final DefaultSVNMergerAction MARK_CONFLICTED = new DefaultSVNMergerAction();
    
    public static final DefaultSVNMergerAction CHOOSE_BASE = new DefaultSVNMergerAction();
    public static final DefaultSVNMergerAction CHOOSE_REPOSITORY = new DefaultSVNMergerAction();
    public static final DefaultSVNMergerAction CHOOSE_WORKING = new DefaultSVNMergerAction();

    public static final DefaultSVNMergerAction MARK_RESOLVED = new DefaultSVNMergerAction();
    public static final DefaultSVNMergerAction CHOOSE_MERGED_FILE = new DefaultSVNMergerAction();
    public static final DefaultSVNMergerAction CHOOSE_REPOSITORY_CONFLICTED = new DefaultSVNMergerAction();
    public static final DefaultSVNMergerAction CHOOSE_WORKING_CONFLICTED = new DefaultSVNMergerAction();
    
    private DefaultSVNMergerAction() {
    }
}
