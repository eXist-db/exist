/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage;

import java.util.Iterator;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.exist.source.Source;
import org.exist.util.hashtable.Object2ObjectHashMap;
import org.exist.xquery.CompiledXQuery;


/**
 * @author wolf
 */
public class XQueryPool extends Object2ObjectHashMap {

    private final static int MAX_STACK_SIZE = 5;
    
    private final static long TIMEOUT = 120000;
    private final static long TIMEOUT_CHECK_INTERVAL = 30000;
    
    private final static Logger LOG = Logger.getLogger(XQueryPool.class);
    
    private long lastTimeOutCheck;
    
    /**
     * 
     */
    protected XQueryPool() {
        super(27);
        lastTimeOutCheck = System.currentTimeMillis();
    }

    public synchronized void returnCompiledXQuery(Source source, CompiledXQuery xquery) {
        Stack stack = (Stack)get(source);
        if(stack == null) {
            stack = new Stack();
            source.setCacheTimestamp(System.currentTimeMillis());
            put(source, stack);
        }
        if(stack.size() < MAX_STACK_SIZE) {
            stack.push(xquery);
        }
        timeoutCheck();
    } 
    
    public synchronized CompiledXQuery borrowCompiledXQuery(Source source) {
        int idx = getIndex(source);
        if(idx < 0)
            return null;
        Source key = (Source)keys[idx];
        int validity = key.isValid();
        if(validity == Source.UNKNOWN)
            validity = key.isValid(source);
        if(validity == Source.INVALID || validity == Source.UNKNOWN) {
            keys[idx] = REMOVED;
            values[idx] = null;
            LOG.debug(source.getKey() + " is invalid");
            return null;
        }
        Stack stack = (Stack)values[idx];
        if(stack != null && !stack.isEmpty()) {
            return (CompiledXQuery)stack.pop();
        }
        return null;
    }
    
    private void timeoutCheck() {
        final long currentTime = System.currentTimeMillis();
        if(currentTime - lastTimeOutCheck < TIMEOUT_CHECK_INTERVAL)
            return;
        
        for(Iterator i = iterator(); i.hasNext(); ) {
            Source next = (Source)i.next();
            if(currentTime - next.getCacheTimestamp() > TIMEOUT)
                remove(next);
        }
    }
}
