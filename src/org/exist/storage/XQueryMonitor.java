/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.util.ExpressionDumper;

/**
 * Class to keep track of all running queries in a database instance. The main
 * purpose of this class is to signal running queries that the database is going to
 * shut down. This is done through the {@link org.exist.xquery.XQueryWatchDog}
 * registered by each query. It is up to the query to check the watchdog's state.
 * If it simply ignores the terminate signal, it will be killed after the shutdown
 * timeout is reached.
 * 
 * @author wolf
 */
public class XQueryMonitor {

	private final static Logger LOG = Logger.getLogger(XQueryMonitor.class);
	
	private Set runningQueries = new HashSet();
	
	public XQueryMonitor() {
		super();
	}

	public void queryStarted(XQueryWatchDog watchdog) {
		runningQueries.add(watchdog);
	}
	
	public void queryCompleted(XQueryWatchDog watchdog) {
		runningQueries.remove(watchdog);
	}
	
	public void killAll(long waitTime) {
		XQueryWatchDog watchdog;
		for(Iterator i = runningQueries.iterator(); i.hasNext(); ) {
			watchdog = (XQueryWatchDog) i.next();
			LOG.debug("Killing query: " + 
			        ExpressionDumper.dump(watchdog.getContext().getRootExpression()));
			watchdog.kill(waitTime);
		}
	}
}
