/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.deadlocks;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xquery.FunctionFactory;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class GetReleaseBrokerDeadlocks {

	private static final Logger LOG = LogManager.getLogger(GetReleaseBrokerDeadlocks.class);
	
	private static Random rd = new Random();

	@Test
	@Ignore
	public void exterServiceMode() {
		try { 
	        Configuration config = new Configuration();
	        config.setProperty(FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS, Boolean.FALSE);
	        BrokerPool.configure(1, 5, config);
	        
	        Database db = BrokerPool.getInstance();
	        
	        Thread thread = new Thread(db.getThreadGroup(), new EnterServiceMode());

	        try(final DBBroker broker = db.getBroker()) {
	        	thread.start();
		        Thread.sleep(1000);
	        }
	        
	        Thread.sleep(1000);
	        
	        assertFalse(thread.isAlive());
	        
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	class EnterServiceMode implements Runnable {

		@Override
		public void run() {
	        try {
	        	BrokerPool db = BrokerPool.getInstance();
				
	        	Subject subject = db.getSecurityManager().getSystemSubject();
				try {
					db.enterServiceMode(subject);
					
					//do something
					Thread.sleep(100);
				} finally {
					db.exitServiceMode(subject);
				}
				
			} catch (Exception e) {
				fail(e.getMessage());
			}
		}
	}

	@Test
	@Ignore
	public void testingGetReleaseCycle() {
		boolean debug = false;
		try { 
	        Configuration config = new Configuration();
	        config.setProperty(FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS, new Boolean(false));
	        BrokerPool.configure(1, 5, config);
	        
	        Database db = BrokerPool.getInstance();
	        
	        Thread thread;
	        for (int i = 0; i < 1000; i++) {
	        	thread = new Thread(db.getThreadGroup(), new GetRelease());
	        	thread.start();
		        Thread.sleep(rd.nextInt(250));
		        
		        if (ex != null) {
		        	LOG.error(ex.getMessage(), ex);
					fail(ex.getMessage());
		        }
		        
		        if (debug && db.countActiveBrokers() == 20) {
		    		Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
		    		
		    		StringBuilder sb = new StringBuilder();
		    		
	            	sb.append("************************************************\n");
	            	sb.append("************************************************\n");

	            	for (Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
		            	
		            	StackTraceElement[] stacks = entry.getValue();

		            	sb.append("THREAD: ");
		            	sb.append(entry.getKey().getName());
	            		sb.append("\n");
                        for (StackTraceElement stack : stacks) {
                            sb.append(" ");
                            sb.append(stack);
                            sb.append("\n");
                        }
		            }
		            
		            if (stackTraces.isEmpty())
		            	sb.append("No threads.");
		            
//		            System.out.println(sb.toString());
		        }
	        }
	        
	        while (db.countActiveBrokers() > 0) {
		        Thread.sleep(100);
	        }
	        	
	        
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			fail(e.getMessage());
		}
	}

	private static Throwable ex = null;
	
	class GetRelease implements Runnable {

		@Override
		public void run() {
	        try {
	        	BrokerPool db = BrokerPool.getInstance();

				try(final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {

					
					//do something
					Thread.sleep(rd.nextInt(5000));
					
					try(final DBBroker currentBroker = db.getBroker()) {
						assertEquals(broker, currentBroker);
	
						//do something
						Thread.sleep(rd.nextInt(5000));
					}
				}
				
			} catch (Throwable e) {
				ex = e;
			}
		}
	}
}