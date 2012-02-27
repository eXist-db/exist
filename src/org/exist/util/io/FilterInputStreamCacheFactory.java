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

import java.io.IOException;

/**
 * Factory to instantiate a cache object
 * takes a different behaviour on Windows to Unix.
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class FilterInputStreamCacheFactory {

    private final static boolean WINDOWS_PLATFORM;
    static {
        final String osName = System.getProperty("os.name");
        WINDOWS_PLATFORM = (osName != null && osName.toLowerCase().startsWith("windows"));   
    }
    
    /**
     * Get a suitable Cache instance
     * 
     * By default, on Windows platforms FileFilterInputStreamCache is used
     * on other platforms MemoryMappedFileFilterInputStreamCache.
     * This is because Users reported problems with the
     * memory use of MemoryMappedFileFilterInputStreamCache on Windows.
     * 
     * The class used can be overriden by setting the system property 'filterInputStreamCache=type'
     * where type is one of 'file', 'memoryMapped', or 'memory'.
     * 
     * file: Random IO on a temporary file
     * memoryMapped: Memory Mapped IO on a temporary file (fast for multiple reads)
     * memory: All cache is kept in RAM (very fast for everything)
     */
    public static FilterInputStreamCache getCacheInstance() throws IOException {
       final String cacheType = System.getProperty("filterInputStreamCache", "");
       
       if(cacheType.equals("file")) {
           return new FileFilterInputStreamCache();
       } else if (cacheType.equals("memoryMapped")) {
           return new MemoryMappedFileFilterInputStreamCache();
       } else if(cacheType.equals("memory")) {
            return new MemoryFilterInputStreamCache();
       } else {
           
           //TODO enable MemoryMappedFileFilterInputStreamCache as the default on non-windows platforms
           
           //if(WINDOWS_PLATFORM) {
               //return new FileFilterInputStreamCache();
           //} else {
              return new MemoryMappedFileFilterInputStreamCache();
           //}
       }
    }   
}