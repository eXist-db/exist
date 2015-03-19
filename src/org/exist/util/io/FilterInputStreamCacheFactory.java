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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory to instantiate a cache object
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class FilterInputStreamCacheFactory {

    private final static Logger LOG = LogManager.getLogger(FilterInputStreamCacheFactory.class);
    
    public interface FilterInputStreamCacheConfiguration {
        public String getCacheClass();
    }
    
    private FilterInputStreamCacheFactory() {
    }
    
    /**
     * Get a suitable Cache instance
     * 
     */
    public static FilterInputStreamCache getCacheInstance(final FilterInputStreamCacheConfiguration cacheConfiguration) throws IOException {
       
        final FilterInputStreamCache cache = new FilterInputStreamCacheFactory().instantiate(cacheConfiguration);
        if(cache == null) {
            throw new IOException("Could not load cache for class: " + cacheConfiguration.getCacheClass());
        }
        return cache;
    }
    
    private FilterInputStreamCache instantiate(final FilterInputStreamCacheConfiguration cacheConfiguration) {
        try {
            final Class clazz = Class.forName(cacheConfiguration.getCacheClass());
            
            final Object obj = clazz.newInstance();
            
            if(!(obj instanceof FilterInputStreamCache)) {
                LOG.error("Invalid cache class: " + clazz.getName());
                return null;
            }
            
            return (FilterInputStreamCache)obj;
            
        } catch(final ClassNotFoundException cnfe) {
           LOG.error(cnfe.getMessage(), cnfe);
           return null;
        } catch(final InstantiationException ie) {
           LOG.error(ie.getMessage(), ie);
           return null;
        } catch(final IllegalAccessException iae) {
           LOG.error(iae.getMessage(), iae);
           return null;
        }
    }
}