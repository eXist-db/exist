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
package org.exist.versioning.svn.core.io.diff;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;


/**
 * The <b>SVNDeltaProcessor</b> is used to get a full text of a file 
 * in series applying diff windows being passed to a processor.  
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNDeltaProcessor {
    
    private SVNDiffWindowApplyBaton myApplyBaton;

    /**
     * Creates a processor. 
     */
    public SVNDeltaProcessor() {
    }
    
    /**
     * Starts processing deltas given a base file stream and an output stream 
     * to write resultant target bytes to.
     * 
     * <p>
     * If a target full text is a newly added file (text deltas would be vs. empty), 
     * then source bytes are not needed and <code>base</code> may be passed as 
     * <span class="javakeyword">null</span>.
     * 
     * <p>
     * If <code>computeChecksum</code> is <span class="javakeyword">true</span>, then 
     * an MD5 checksum will be calculated for target bytes. The calculated checksum is 
     * returned by {@link #textDeltaEnd()}.
     * 
     * @param base             an input stream to take base file contents 
     *                         from
     * @param target           an output stream to write the resultant target 
     *                         contents to
     * @param computeCheksum   <span class="javakeyword">true</span> to calculate
     *                         checksum
     */
    public void applyTextDelta(InputStream base, OutputStream target, boolean computeCheksum) {
        reset();
        MessageDigest digest = null;
        try {
            digest = computeCheksum ? MessageDigest.getInstance("MD5") : null;
        } catch (NoSuchAlgorithmException e1) {
        }
        base = base == null ? SVNFileUtil.DUMMY_IN : base;
        myApplyBaton = FakeSVNDiffWindowApplyBaton.create(base, target, digest);
    }
    
    /**
     * Starts processing deltas given a base file and a one 
     * to write resultant target bytes to.
     * 
     * <p>
     * If a target full text is a newly added file (text deltas would be vs. empty), 
     * then source bytes are not needed and <code>baseFile</code> may be passed as 
     * <span class="javakeyword">null</span>.
     * 
     * <p>
     * If a file represented by <code>targetFile</code> does not exist 
     * yet, first tries to create an empty file.
     * 
     * <p>
     * If <code>computeChecksum</code> is <span class="javakeyword">true</span>, then 
     * an MD5 checksum will be calculated for target bytes. The calculated checksum is 
     * returned by {@link #textDeltaEnd()}. 
     * 
     * @param  baseFile          a base file to read base file contents 
     *                           from
     * @param  targetFile        a destination file where resultant 
     *                           target bytes will be written
     * @param  computeCheksum    <span class="javakeyword">true</span> to calculate
     *                           checksum
     * @throws SVNException
     */
    public void applyTextDelta(File baseFile, File targetFile, boolean computeCheksum) throws SVNException {
        if (!targetFile.exists()) {
            SVNFileUtil.createEmptyFile(targetFile);
        }
        InputStream base = baseFile != null && baseFile.exists() ? SVNFileUtil.openFileForReading(baseFile) : SVNFileUtil.DUMMY_IN;
        applyTextDelta(base, SVNFileUtil.openFileForWriting(targetFile), computeCheksum);
    }

    /**
     * Starts processing deltas given a base file and a one 
     * to write resultant target bytes to.
     * 
     * <p>
     * If a target full text is a newly added file (text deltas would be vs. empty), 
     * then source bytes are not needed and <code>baseIS</code> may be passed as 
     * <span class="javakeyword">null</span>.
     * 
     * <p>
     * If a file represented by <code>targetFile</code> does not exist 
     * yet, first tries to create an empty file.
     * 
     * <p>
     * If <code>computeTargetChecksum</code> is <span class="javakeyword">true</span>, then 
     * an MD5 checksum will be calculated for target bytes. The calculated checksum is 
     * returned by {@link #textDeltaEnd()}. 
     * 
     * @param  baseIS                an input stream to take base file contents 
     *                               from 
     * @param  targetFile            a destination file where resultant 
     *                               target bytes will be written
     * @param  computeTargetCheksum  <span class="javakeyword">true</span> to calculate
     *                               checksum of the target text
     * @throws SVNException
     * @since  1.3
     */
    public void applyTextDelta(InputStream baseIS, File targetFile, boolean computeTargetCheksum) throws SVNException {
        if (!targetFile.exists()) {
            SVNFileUtil.createEmptyFile(targetFile);
        }
        applyTextDelta(baseIS, SVNFileUtil.openFileForWriting(targetFile), computeTargetCheksum);
    }

    /**
     * Receives a next diff window to be applied. The return value is a 
     * dummy stream (left for backward compatibility) since new data should 
     * come within a diff window.
     * 
     * @param   window           a diff window
     * @return                   a dummy output stream
     * @throws  SVNException
     */
    public OutputStream textDeltaChunk(SVNDiffWindow window) throws SVNException {
        window.apply(myApplyBaton);
        return SVNFileUtil.DUMMY_OUT;
    }
    
    private void reset() {
        if (myApplyBaton != null) {
            myApplyBaton.close();
            myApplyBaton = null;
        }
    }
    
    /**
     * Performs delta processing finalizing steps. Applies the last 
     * window left (if any) and finalizes checksum calculation (if a 
     * checksum was required).  
     *  
     * @return  a string representing a hex form of the calculated
     *          MD5 checksum or <span class="javakeyword">null</span> 
     *          if checksum calculation was not required 
     */
    public String textDeltaEnd() {
        try {
            return myApplyBaton.close();
        } finally { 
            reset();
        }
    }
}
