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
package org.exist.extensions.exquery.restxq.impl;

import java.net.URI;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.exist.extensions.exquery.restxq.RestXqServiceCompiledXQueryCache;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceException;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

/**
 * Compiled XQuery Cache for RESTXQ Resource Functions.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RestXqServiceCompiledXQueryCacheImpl implements RestXqServiceCompiledXQueryCache {
    
    private static final RestXqServiceCompiledXQueryCacheImpl INSTANCE = new RestXqServiceCompiledXQueryCacheImpl();

    // TODO(AR) make configurable?
    private static final int DEFAULT_MAX_POOL_SIZE = 256;
    private static final int DEFAULT_MAX_QUERY_STACK_SIZE = 64;

    private final Cache<URI, Queue<CompiledXQuery>> cache;

    private RestXqServiceCompiledXQueryCacheImpl() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_POOL_SIZE)
                .build();
    }

    public static RestXqServiceCompiledXQueryCacheImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public CompiledXQuery getCompiledQuery(final DBBroker broker, final URI xqueryLocation)
            throws RestXqServiceException {
        final Queue<CompiledXQuery> queue =
                cache.get(xqueryLocation,
                        key -> new MpmcAtomicArrayQueue<>(DEFAULT_MAX_QUERY_STACK_SIZE));

        CompiledXQuery xquery = queue.poll();
        if(xquery == null) {
            xquery = XQueryCompiler.compile(broker, xqueryLocation);
        } else {
            // prepare the context for re-use
            try {
                xquery.getContext().prepareForReuse();
            } catch (final XPathException e) {
                throw new RestXqServiceException("Unable to prepare compiled XQuery for reuse", e);
            }
        }
        xquery.getContext().prepareForExecution();

        return xquery;
    }
    
    @Override
    public void returnCompiledQuery(final URI xqueryLocation, final CompiledXQuery xquery) {
        //reset the query and context
        xquery.getContext().runCleanupTasks();
        xquery.reset();
        xquery.getContext().reset();

        // place in the cache
        final Queue<CompiledXQuery> queue =
                cache.get(xqueryLocation,
                        key -> new MpmcAtomicArrayQueue<>(DEFAULT_MAX_QUERY_STACK_SIZE));
        queue.offer(xquery);
    }
    
    @Override
    public void removeService(final RestXqService service) {
        final URI xqueryLocation = service.getResourceFunction().getXQueryLocation();
        cache.invalidate(xqueryLocation);
    }
    
    @Override
    public void removeServices(final Iterable<RestXqService> services) {
        cache.invalidateAll(mapIterable(services,
                restXqService -> restXqService.getResourceFunction().getXQueryLocation()));
    }

    /**
     * Utility function that given an Iterable<T> and a function of T -> U, returns an Iterable<U>.
     *
     * @param <T> The input type.
     * @param <U> The mapped result type.
     *
     * @param input The input iterable.
     * @param mapper The mapping function.
     *
     * @return The mapped iterable.
     */
    private static <T, U> Iterable<U> mapIterable(final Iterable<T> input, final Function<T, U> mapper) {
        return new Iterable<U>() {
            @Override
            public Iterator<U> iterator() {
                final Iterator<T> it = input.iterator();
                return new Iterator<U>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public U next() {
                        return mapper.apply(it.next());
                    }
                };
            }
        };
    }
}
