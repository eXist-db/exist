/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import java.io.*;

/**
 * Cache implementation for CachingFilterInputStream
 * Backed by a Random Access File
 * 
 * Probably slowed than MemoryMappedFileFilterInputStreamCache
 * for multiple reads, but uses a fixed small amount of memory.
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class FileFilterInputStreamCache implements FilterInputStreamCache {
    private final File tempFile;
    private final boolean externalFile;
    private int length = 0;
    private int offset = 0;
    
    private final RandomAccessFile raf;

    public FileFilterInputStreamCache() throws IOException {
        this(null);
    }
    
    public FileFilterInputStreamCache(File f) throws IOException {
         if(f == null) {
            tempFile = TemporaryFileManager.getInstance().getTemporaryFile();
            externalFile = false;
        } else {
            tempFile = f;
            externalFile = true;
        }
         
        this.raf = new RandomAccessFile(tempFile, "rw");
    }
    
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        raf.write(b, off, len);
        length += (len - off);
        offset += (len - off);
    }

    @Override
    public void write(int i) throws IOException {
        raf.write(i);
        length++;
        offset++;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public byte get(int off) throws IOException {
        if(off != offset) {
            raf.seek(off);
            this.offset = off;
        }
        return raf.readByte();
    }

    @Override
    public void copyTo(int cacheOffset, byte[] b, int off, int len) throws IOException {
        if(off != offset) {
            raf.seek(off);
            this.offset = off;
        }
        raf.readFully(b, off, len);
    }

    @Override
    public void invalidate() throws IOException {
        
        raf.close();
        
        if(tempFile != null && (!externalFile)) {
           TemporaryFileManager.getInstance().returnTemporaryFile(tempFile);
        }
    }
}