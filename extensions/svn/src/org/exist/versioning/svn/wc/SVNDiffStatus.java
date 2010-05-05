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

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;


/**
 * The <b>SVNDiffStatus</b> class is used to provide short information on path changes
 * during diff status operations. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNDiffStatus {
    
    private SVNStatusType myModificationType;
    private boolean myIsPropertiesModified;
    private SVNNodeKind myKind;
    private SVNURL myURL;
    private String myPath;
    private File myFile;
    
    /**
     * Instantiates a new object.
     * 
     * @param file             a wc item path
     * @param url              an item url
     * @param path             a relative item path (may be <span class="javakeyword">null</span>)
     * @param type             a type of path change
     * @param propsModified    sets whether properties are modified
     * @param kind             a path kind (dir or file)  
     */
    public SVNDiffStatus(File file, SVNURL url, String path, SVNStatusType type, boolean propsModified, SVNNodeKind kind) {
        myURL = url;
        myPath = path;
        myModificationType = type;
        myIsPropertiesModified = propsModified;
        myKind = kind;
        myFile = file;
    }
    
    /**
     * Returns File representation of the Working Copy item path. 
     * 
     * @return wc item path as File 
     */
    public File getFile() {
        return myFile;
    }
    
    /**
     * Says whether properties of the Working Copy item are modified. 
     *  
     * @return <span class="javakeyword">true</span> if properties were modified
     *         in a particular revision, <span class="javakeyword">false</span> 
     *         otherwise
     */
    public boolean isPropertiesModified() {
        return myIsPropertiesModified;
    }
    
    /**
     * Returns the node kind of the Working Copy item. 
     * 
     * @return node kind
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Returns the type of modification for the current 
     * item. 
     * 
     * @return a path change type
     */
    public SVNStatusType getModificationType() {
        return myModificationType;
    }    
    
    /**
     * Returns a relative path of the item. 
     * Set for Working Copy items and relative to the anchor of diff status operation.
     * 
     * @return item path
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Url of the item.
     * 
     * @return item url
     */
    public SVNURL getURL() {
        return myURL;
    }

}
