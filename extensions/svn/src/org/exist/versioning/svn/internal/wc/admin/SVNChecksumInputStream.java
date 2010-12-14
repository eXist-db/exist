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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.exist.versioning.svn.internal.wc.SVNFileUtil;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNChecksumInputStream extends DigestInputStream {
    
    public static final String MD5_ALGORITHM = "MD5";
    private static byte[] ourDirtyBuffer = new byte[4096*4];
    
    public SVNChecksumInputStream(InputStream source, String algorithm) {
        super(source, null);
        
        algorithm = algorithm == null ? MD5_ALGORITHM : algorithm;
        try {
            setMessageDigest(MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException e) {
        }
        on(getMessageDigest() != null);
    }

    public void close() throws IOException {
        int r = 0;
        do {
            r = read(ourDirtyBuffer);
        } while(r >= 0);
        super.close();
    }
    
    public String getDigest() {
        return getMessageDigest() != null ? SVNFileUtil.toHexDigest(getMessageDigest().digest()) : null;
    }

}
