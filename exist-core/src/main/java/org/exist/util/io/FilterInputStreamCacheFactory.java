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
import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.lang.invoke.MethodType.methodType;

/**
 * Factory to instantiate a cache object
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class FilterInputStreamCacheFactory {

    private static final Logger LOG = LogManager.getLogger(FilterInputStreamCacheFactory.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public interface FilterInputStreamCacheConfiguration {
        String getCacheClass();
    }

    private FilterInputStreamCacheFactory() {
    }

    /**
     * Get a suitable Cache instance.
     *
     * @param cacheConfiguration the configuration for the cache
     * @param is the input stream to cache
     *
     * @return the cache instance
     *
     * @throws IOException if an error occurs setting up the cache
     */
    public static FilterInputStreamCache getCacheInstance(final FilterInputStreamCacheConfiguration cacheConfiguration, final InputStream is) throws IOException {
        final FilterInputStreamCache cache = new FilterInputStreamCacheFactory().instantiate(cacheConfiguration, is);
        if (cache == null) {
            throw new IOException("Could not load cache for class: " + cacheConfiguration.getCacheClass());
        }
        FilterInputStreamCacheMonitor.getInstance().register(cache);
        return cache;
    }

    private FilterInputStreamCache instantiate(final FilterInputStreamCacheConfiguration cacheConfiguration, final InputStream is) {
        try {
            final Class clazz = Class.forName(cacheConfiguration.getCacheClass());

            final MethodHandle methodHandle = LOOKUP.findConstructor(clazz, methodType(void.class, InputStream.class));

            final Function<InputStream, FilterInputStreamCache> constructor = (Function<InputStream, FilterInputStreamCache>)
                    LambdaMetafactory.metafactory(
                            LOOKUP, "apply", methodType(Function.class),
                            methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();
            return constructor.apply(is);
        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            LOG.error(e.getMessage(), e);
            return null;
        }
    }
}
