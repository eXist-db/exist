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
package org.exist.versioning.svn.internal.wc;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;



/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCancellableOutputStream extends FilterOutputStream {

    private ISVNCanceller myEventHandler;

    public SVNCancellableOutputStream(OutputStream out, ISVNCanceller eventHandler) {
        super(out == null ? SVNFileUtil.DUMMY_OUT : out);
        myEventHandler = eventHandler;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (myEventHandler != null) {
            try {
                myEventHandler.checkCancelled();
            } catch (final SVNCancelException e) {
                throw new IOCancelException(e.getMessage());
            }
        }
        out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        if (myEventHandler != null) {
            try {
                myEventHandler.checkCancelled();
            } catch (final SVNCancelException e) {
                throw new IOCancelException(e.getMessage());
            }
        }
        out.write(b);
    }
    
    /**
     * @version 1.3
     * @author  TMate Software Ltd.
     */
    public static class IOCancelException extends IOException {

        private static final long serialVersionUID = 4845L;

        public IOCancelException(String message) {
            super(message);
        }
    }
}
