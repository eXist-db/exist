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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea15 extends SVNAdminArea14 {

    public static final int WC_FORMAT = SVNAdminArea15Factory.WC_FORMAT;

    protected static final String ATTRIBUTE_KEEP_LOCAL = "keep-local";

    private static final Set INAPPLICABLE_PROPERTIES = new SVNHashSet();

    static {
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_PATH);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_REVISION);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_PEG_REVISION);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.TREE_CONFLICT_DATA);
    }

    public SVNAdminArea15(File dir) {
        super(dir);
    }

    public int getFormatVersion() {
        return WC_FORMAT;
    }

    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        String line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String changelist = parseString(line);
        if (changelist != null) {
            entryAttrs.put(SVNProperty.CHANGELIST, changelist);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        boolean keepLocal = parseBoolean(line, ATTRIBUTE_KEEP_LOCAL);
        if (keepLocal) {
            entryAttrs.put(SVNProperty.KEEP_LOCAL, SVNProperty.toString(keepLocal));
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String workingSize = parseString(line);
        if (workingSize != null) {
            entryAttrs.put(SVNProperty.WORKING_SIZE, workingSize);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String depthStr = parseValue(line);
        if (depthStr == null) {
            entryAttrs.put(SVNProperty.DEPTH, SVNDepth.INFINITY.getName());
        } else {
            entryAttrs.put(SVNProperty.DEPTH, depthStr);
        }
        return false;
    }

    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        emptyFields = super.writeExtraOptions(writer, entryName, entryAttrs, emptyFields);
        
        String changelist = (String) entryAttrs.get(SVNProperty.CHANGELIST); 
        if (writeString(writer, changelist, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String keepLocalAttr = (String) entryAttrs.get(SVNProperty.KEEP_LOCAL);
        if (SVNProperty.booleanValue(keepLocalAttr)) {
            writeValue(writer, ATTRIBUTE_KEEP_LOCAL, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String workingSize = (String) entryAttrs.get(SVNProperty.WORKING_SIZE);
        workingSize = "-1".equals(workingSize) ? null : workingSize;
        if (writeString(writer, workingSize, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        boolean isThisDir = getThisDirName().equals(entryName);
        boolean isSubDir = !isThisDir && SVNProperty.KIND_DIR.equals(entryAttrs.get(SVNProperty.KIND)); 
        String depthStr = (String) entryAttrs.get(SVNProperty.DEPTH);
        SVNDepth depth = SVNDepth.fromString(depthStr);
        if ((isSubDir && depth != SVNDepth.EXCLUDE) || depth == SVNDepth.INFINITY) {
            emptyFields++;
        } else {
            if (writeValue(writer, depthStr, emptyFields)) {
                emptyFields = 0;    
            } else {
                ++emptyFields;
            }
        }

        return emptyFields;
    }

    protected SVNAdminArea createAdminAreaForDir(File dir) {
        return new SVNAdminArea15(dir);
    }

    protected boolean isEntryPropertyApplicable(String propName) {
        return propName != null && !INAPPLICABLE_PROPERTIES.contains(propName);
    }
}
