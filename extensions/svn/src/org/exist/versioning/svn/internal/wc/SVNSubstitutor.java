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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSubstitutor {
    
    private static final byte[] ALL = new byte[] {'$', '\r', '\n'};
    private static final byte[] EOLS = new byte[] {'\r', '\n'};
    private static final byte[] KEYWORDS = new byte[] {'$'};
    private static final int KEYWORD_MAX_LENGTH = 255;
    
    private boolean myIsRepair;
    private boolean myIsExpand;
    private Map myKeywords;
    
    private byte[] myEOL;
    private byte[] myLastEOL;
    private byte[] myInteresting;
    private byte[] myEOLBuffer;
    private byte[] myKeywordBuffer;
    
    private int[] myLastEOLLength = new int[] {0};
    private int myKeywordBufferLength;
    private int myEOLBufferLength;

    public SVNSubstitutor(byte[] eol, boolean repair, Map keywords, boolean expand) {
        myEOL = eol;
        myKeywords = keywords;
        myIsExpand = expand;
        myIsRepair = repair;
        myInteresting = eol != null && keywords != null ? ALL : (eol != null ? EOLS : KEYWORDS);
        
        myEOLBuffer = new byte[2];
        myLastEOL = new byte[2];
        myKeywordBuffer = new byte[KEYWORD_MAX_LENGTH];
        
        myEOLBufferLength = 0;
        myKeywordBufferLength = 0;
    }
    
    public ByteBuffer translateChunk(ByteBuffer src, ByteBuffer dst) throws SVNException {
        if (src != null) {
            int nextSignOff = 0;            
            while(src.hasRemaining()) {
                byte p = src.get(src.position());
                if (myEOLBufferLength > 0) {
                    if (p == '\n') {
                        myEOLBuffer[myEOLBufferLength++] = src.get();
                    }
                    dst = substituteEOL(dst, myEOL, myEOL.length, 
                            myLastEOL, myLastEOLLength, 
                            myEOLBuffer, myEOLBufferLength, myIsRepair);
                    myEOLBufferLength = 0;
                } else if (myKeywordBufferLength > 0 && p == '$') {
                    myKeywordBuffer[myKeywordBufferLength++] = src.get();                    
                    byte[] keywordName = matchKeyword(myKeywordBuffer, 0, myKeywordBufferLength);
                    if (keywordName == null) {
                        myKeywordBufferLength--;
                        unread(src, 1);
                    }
                    int newLength = -1;
                    if (keywordName == null || (newLength = translateKeyword(myKeywordBuffer, 0, myKeywordBufferLength, keywordName)) >= 0 || myKeywordBufferLength >= KEYWORD_MAX_LENGTH) {
                        if (newLength >= 0) {
                            myKeywordBufferLength = newLength;
                        }
                        dst = write(dst, myKeywordBuffer, 0, myKeywordBufferLength);
                        nextSignOff = 0;
                        myKeywordBufferLength = 0;                        
                    } else {
                        if (nextSignOff == 0) {
                            nextSignOff = myKeywordBufferLength - 1;
                        }
                        continue;
                    }
                } else if (myKeywordBufferLength == KEYWORD_MAX_LENGTH - 1 || (myKeywordBufferLength > 0 && (p == '\r' || p == '\n'))) {
                    if (nextSignOff > 0) {
                        unread(src, myKeywordBufferLength - nextSignOff);
                        myKeywordBufferLength = nextSignOff;
                        nextSignOff = 0;
                    }
                    dst = write(dst, myKeywordBuffer, 0, myKeywordBufferLength);
                    myKeywordBufferLength = 0;
                } else if (myKeywordBufferLength > 0) {
                    myKeywordBuffer[myKeywordBufferLength++] = src.get();
                    continue;
                }
                int len = 0;
                while(src.position() + len < src.limit() && !isInteresting(src.get(src.position() + len))) {
                    len++;
                }
                if (len > 0) {
                    dst = write(dst, src.array(), src.arrayOffset() + src.position(), len);
                }
                src.position(src.position() + len);
                if (src.hasRemaining()) {
                    // setup interesting.
                    p = src.get();
                    switch (p) {
                        case '$':
                            myKeywordBuffer[myKeywordBufferLength++] = p;
                            break;
                        case '\r':
                            myEOLBuffer[myEOLBufferLength++] = p;
                            break;
                        case '\n':
                            myEOLBuffer[myEOLBufferLength++] = p;
                            dst = substituteEOL(dst, 
                                    myEOL, myEOL.length, 
                                    myLastEOL, myLastEOLLength, 
                                    myEOLBuffer, myEOLBufferLength, myIsRepair);
                            myEOLBufferLength = 0;
                            break;
                    }
                }                
            }
        } else {
            // flush buffers if any.
            if (myEOLBufferLength > 0) {
                dst = substituteEOL(dst, 
                        myEOL, myEOL.length, 
                        myLastEOL, myLastEOLLength, 
                        myEOLBuffer, myEOLBufferLength, myIsRepair);
                myEOLBufferLength = 0;
            }
            if (myKeywordBufferLength > 0) {
                dst = write(dst, myKeywordBuffer, 0, myKeywordBufferLength);
                myKeywordBufferLength = 0;
            }
        }
        return dst;
    }
    
    private boolean isInteresting(byte p) {
        for(int i = 0; i < myInteresting.length; i++) {
            if (p == myInteresting[i]) {
                return true;
            }
        }
        return false;
    }
    
    private byte[] matchKeyword(byte[] src, int offset, int length) { 
        if (myKeywords == null) {
            return null;
        }
        String name = null;
        int len = 0;
        try {
            for(int i = 0; i < length - 2 && src[offset + i + 1] != ':'; i++) {
                len++;
            }
            if (len == 0) {
                return null;
            }
            name = new String(src, offset + 1, len, "ASCII");
        } catch (UnsupportedEncodingException e) {
            //
        } 
        if (name != null && myKeywords.containsKey(name)) {
            byte[] nameBytes = new byte[len];
            System.arraycopy(src, offset + 1, nameBytes, 0, len);
            return nameBytes;
        }
        return null;
    }
    
    private int translateKeyword(byte[] src, int offset, int length, byte[] name) {
        if (myKeywords == null) {
            return -1;
        }
        String nameStr;
        try {
            nameStr = new String(name, "ASCII");
        } catch (UnsupportedEncodingException e) {
            return -1;
        }
        byte[] value = (byte[]) myKeywords.get(nameStr);
        if (myKeywords.containsKey(nameStr)) {
            if (!myIsExpand) {
                value = null;
            }
            return substituteKeyword(src, offset, length, name, value);
        }
        return -1;
    }
    
    private static void unread(ByteBuffer buffer, int length) {
        buffer.position(buffer.position() - length);
    }
    
    private static int substituteKeyword(byte[] src, int offset, int length, byte[] keyword, byte[] value) {
        int pointer;
        if (length < keyword.length + 2) {
            return -1;
        }
        for(int i = 0; i < keyword.length; i++) {
            if (keyword[i] != src[offset + 1 + i]) {
                return -1;
            }
        }
        pointer = offset + 1 + keyword.length;
        if (src[pointer] == ':' && src[pointer + 1] == ':' && src[pointer + 2] == ' ' && 
                (src[offset + length - 2] == ' ' || src[offset + length - 2] == '#') &&
                6 + keyword.length < length) {
            // fixed size keyword.
            if (value == null) {
                pointer += 2;
                while(src[pointer] != '$') {
                    src[pointer++] = ' ';
                }
            } else {
                int maxValueLength = length - (6 + keyword.length);
                if (value.length <= maxValueLength) {
                    // put value, then spaces.
                    System.arraycopy(value, 0, src, pointer + 3, value.length);
                    pointer += 3 + value.length;
                    while(src[pointer] != '$') {
                        src[pointer++] = ' ';
                    }
                } else {
                    System.arraycopy(value, 0, src, pointer + 3, maxValueLength);
                    src[offset + length - 2] = '#';
                    src[offset + length - 1] = '$';
                }
            }
            return length;
        } else if (src[pointer] == '$' || (src[pointer] == ':' && src[pointer + 1] == '$')) {
            if (value != null) {
                src[pointer] = ':';
                src[pointer + 1] = ' ';
                if (value.length > 0) {
                    int valueLength = value.length;
                    if (valueLength > KEYWORD_MAX_LENGTH - 5 - keyword.length) {
                        valueLength = KEYWORD_MAX_LENGTH - 5 - keyword.length;
                    }
                    System.arraycopy(value, 0, src, pointer + 2, valueLength);
                    src[pointer + 2 + valueLength] = ' ';
                    src[pointer + 3 + valueLength] = '$';
                    length = 5 + keyword.length + valueLength;
                } else {
                    src[pointer + 2] = '$';
                    length = 4 + keyword.length;
                }
            }
            return length;
        } else if (length >= keyword.length + 4 && src[pointer] == ':' && src[pointer + 1] == ' ' && src[offset + length - 2] == ' ') {
            if (value == null) {
                src[pointer] = '$';
                length = 2 + keyword.length;
            } else {
                src[pointer] = ':';
                src[pointer + 1] = ' ';
                if (value.length > 0) {
                    int valueLength = value.length;
                    if (valueLength > KEYWORD_MAX_LENGTH - 5 - keyword.length) {
                        valueLength = KEYWORD_MAX_LENGTH - 5 - keyword.length;
                    }
                    System.arraycopy(value, 0, src, pointer + 2, valueLength);
                    src[pointer + 2 + valueLength] = ' ';
                    src[pointer + 3 + valueLength] = '$';
                    length = 5 + keyword.length + valueLength;
                } else {
                    src[pointer + 2] = '$';
                    length = 4 + keyword.length;
                }
            }
            return length;
        }
        return -1;
    }
    
    private static ByteBuffer substituteEOL(ByteBuffer dst, 
            byte[] eol, int eolLength, byte[] lastEOL, int[] lastEOLLength, byte[] nextEOL, int nextEOLLength, 
            boolean repair) throws SVNException {
        if (lastEOLLength[0] > 0) {
            if (!repair && (lastEOLLength[0] != nextEOLLength || !Arrays.equals(lastEOL, nextEOL))) {
                // inconsistent EOLs.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_INCONSISTENT_EOL);
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
        } else {
            lastEOLLength[0] = nextEOLLength;
            lastEOL[0] = nextEOL[0];
            lastEOL[1] = nextEOL[1];
        }
        return write(dst, eol, 0, eolLength);
    }
    
    private static ByteBuffer write(ByteBuffer dst, byte[] bytes, int offset, int length) {
        if (dst.remaining() < length) {
            ByteBuffer newDst = ByteBuffer.allocate((dst.position() + length)*3/2);
            dst.flip();
            dst = newDst.put(dst);
        }
        return dst.put(bytes, offset, length);
    }
}
