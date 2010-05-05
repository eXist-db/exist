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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.SVNSubstitutor;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNTranslatorOutputStream extends OutputStream {
    
    private SVNSubstitutor mySubstitutor;
    private OutputStream myDst;
    private ByteBuffer mySrcBuffer;
    private ByteBuffer myDstBuffer;

    public SVNTranslatorOutputStream(OutputStream dst, byte[] eol, boolean repair, Map keywords, boolean expand) {
        mySubstitutor = new SVNSubstitutor(eol, repair, keywords, expand);
        myDst = dst;
        mySrcBuffer = ByteBuffer.allocate(2048); 
        myDstBuffer = ByteBuffer.allocate(2048); 
    }
    
    public void write(byte[] b, int off, int len) throws IOException {
        mySrcBuffer = write(mySrcBuffer, b, off, len);        
        mySrcBuffer.flip();
        // now src is ready for reading untill limit.
        try {
            myDstBuffer = mySubstitutor.translateChunk(mySrcBuffer, myDstBuffer);
        } catch (SVNException svne) {
            IOExceptionWrapper wrappedException = new IOExceptionWrapper(svne);
            throw wrappedException;
        }

        myDstBuffer.flip();
        // push all from dst buffer to dst stream.
        myDst.write(myDstBuffer.array(), myDstBuffer.arrayOffset() + myDstBuffer.position(), myDstBuffer.remaining());
        // there should be nothing in src now.
        // and in dst.
        mySrcBuffer.clear();
        myDstBuffer.clear();
    }

    public void close() throws IOException {        
        try {
            myDstBuffer = mySubstitutor.translateChunk(null, myDstBuffer);
        } catch (SVNException svne) {
            IOExceptionWrapper wrappedException = new IOExceptionWrapper(svne);
            throw wrappedException;
        }
        myDstBuffer.flip();
        if (myDstBuffer.hasRemaining()) {
            myDst.write(myDstBuffer.array(), myDstBuffer.arrayOffset() + myDstBuffer.position(), myDstBuffer.remaining());
        }
        myDstBuffer.clear();
        myDst.close();
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(int b) throws IOException {
        write(new byte[] {(byte) (b & 0xFF)});
    }
    
    private static ByteBuffer write(ByteBuffer dst, byte[] bytes, int offset, int length) {
        if (dst.remaining() < length) {
            // expand dst.
            ByteBuffer newDst = ByteBuffer.allocate((dst.position() + length)*3/2);
            dst.flip();
            dst = newDst.put(dst);
        }
        return dst.put(bytes, offset, length);
    }

}
