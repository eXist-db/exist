/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.debuggee;

import java.util.Observable;
import java.util.Observer;

import org.exist.Database;
import org.exist.debuggee.dbgp.packets.Stop;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ScriptRunner implements Runnable, Observer {

	private SessionImpl session;
	private CompiledXQuery expression;

	private Thread thread;
	
	protected Exception exception = null;
	
	public ScriptRunner(SessionImpl session, CompiledXQuery compiled) {
		this.session = session;
		expression = compiled;
		
		thread = new Thread(this);
		thread.setDaemon(true);
		thread.setName("Debug session "+compiled.getContext().hashCode());
	}

	public void start() {
		thread.start();
	}
	
	public void stop() {
		thread.interrupt();
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Database db = null;
		DBBroker broker = null;
		
		try {
			db = BrokerPool.getInstance();
			
            BrokerPool.registerStatusObserver(this);
			
			broker = db.get(null);

	        XQuery xquery = broker.getXQueryService();

	        xquery.execute(expression, null);

//	        XQueryContext context = expression.getContext();
//	        
//	        expression.reset();
//
//	        context.setBroker(broker);
//	        context.getWatchDog().reset();
//
//	        //do any preparation before execution
//	        context.prepare();
//
//	        context.getProfiler().traceQueryStart();
//	        broker.getBrokerPool().getProcessMonitor().queryStarted(context.getWatchDog());
//	        try {
//	        	Sequence result = expression.eval(null);
//	        	
//	        	if(outputProperties != null)
//	        		context.checkOptions(outputProperties); //must be done before context.reset!
//	        	
//	        	//return result;
//	        } finally {
//	            context.getProfiler().traceQueryEnd(context);
//	            expression.reset();
//                context.reset();
//	        	broker.getBrokerPool().getProcessMonitor().queryCompleted(context.getWatchDog());
//	        }
		
        } catch (Exception e) {
        	e.printStackTrace();
        	exception = e;
		} finally {
			if (db != null)
				db.release(broker);
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		if (arg.equals(BrokerPool.SIGNAL_SHUTDOWN)) {

			Stop command = new Stop(session, "");
			command.exec();
			//TODO: make sure that session is closed? what can be done if not?
		}
		
	}
}
