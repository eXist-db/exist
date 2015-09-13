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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.exist.extensions.exquery.restxq.RestXqServiceCompiledXQueryCache;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqServiceCompiledXQueryCacheImpl implements RestXqServiceCompiledXQueryCache {
    
    private final static RestXqServiceCompiledXQueryCacheImpl instance = new RestXqServiceCompiledXQueryCacheImpl();
    
    //TODO could introduce a MAX stack size, i.e. you can only have N compiled main.xqy's in the cache
    private final Map<URI, Deque<CompiledXQuery>> cache = new HashMap<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    public static RestXqServiceCompiledXQueryCacheImpl getInstance() {
        return instance;
    }

    @Override
    public CompiledXQuery getCompiledQuery(final DBBroker broker, final URI xqueryLocation) throws RestXqServiceException {
        
        CompiledXQuery xquery = null;
        cacheLock.writeLock().lock();
        try {
            final Deque<CompiledXQuery> queries = cache.get(xqueryLocation);
            
            if(queries != null && !queries.isEmpty()) {
                xquery = queries.pop();
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        if(xquery == null) {
            xquery = XQueryCompiler.compile(broker, xqueryLocation);
        }
        
        //reset the state of the query
        xquery.reset();
        xquery.getContext().getWatchDog().reset();
        xquery.getContext().prepareForExecution();
        
        return xquery;
    }
    
    @Override
    public void returnCompiledQuery(final URI xqueryLocation, final CompiledXQuery xquery) {
        cacheLock.writeLock().lock();
        try {
            Deque<CompiledXQuery> queries = cache.get(xqueryLocation);
            if(queries == null) {
                queries = new ArrayDeque<>();
            }
            
            //reset the query and context
            xquery.reset();
            xquery.getContext().reset();
            
            queries.push(xquery);
            
            cache.put(xqueryLocation, queries);
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    @Override
    public void removeService(final RestXqService service) {
        cacheLock.writeLock().lock();
        try {        
            cache.remove(service.getResourceFunction().getXQueryLocation());
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    @Override
    public void removeServices(final Iterable<RestXqService> services) {
        cacheLock.writeLock().lock();
        try {
            for(final RestXqService service : services) {
                cache.remove(service.getResourceFunction().getXQueryLocation());
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
}