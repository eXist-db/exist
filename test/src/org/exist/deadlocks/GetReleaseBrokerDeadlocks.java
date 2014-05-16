/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.deadlocks;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Random;

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
	
	private static Random rd = new Random();

	@Test
	@Ignore
	public void exterServiceMode() {
		try { 
	        Configuration config = new Configuration();
	        config.setProperty(FunctionFactory.PROPERTY_DISABLE_DEPRECATED_FUNCTIONS, new Boolean(false));
	        BrokerPool.configure(1, 5, config);
	        
	        Database db = BrokerPool.getInstance();
	        
	        Thread thread = new Thread(new EnterServiceMode());
	        
	        DBBroker broker = null;
	        try {
	        	broker = db.get(null);
	        	
	        	thread.start();
		        Thread.sleep(1000);
	        	
	        } finally {
		        System.out.println("release broker"+Thread.currentThread());
	        	db.release(broker);
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
			        System.out.println("enter servise mode "+Thread.currentThread());
					
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
	        	thread = new Thread(new GetRelease());
	        	thread.start();
		        Thread.sleep(rd.nextInt(250));
		        
		        System.out.println(""+i+", "+db.countActiveBrokers());
		        
		        if (ex != null) {
		        	ex.printStackTrace();
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
		            	for (int n = 0; n < stacks.length; n++) {
		            		sb.append(" ");
		            		sb.append(stacks[n]);
		            		sb.append("\n");
		            	}
		            }
		            
		            if (stackTraces.isEmpty())
		            	sb.append("No threads.");
		            
		            System.out.println(sb.toString());
		        }
	        }
	        
	        while (db.countActiveBrokers() > 0) {
		        System.out.println(db.countActiveBrokers());
		        Thread.sleep(100);
	        }
	        	
	        
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private static Throwable ex = null;
	
	class GetRelease implements Runnable {

		@Override
		public void run() {
	        try {
	        	BrokerPool db = BrokerPool.getInstance();
				
	        	Subject subject = db.getSecurityManager().getSystemSubject();
	        	
	        	DBBroker broker = null;
				try {
					broker = db.get(subject);
			        System.out.println("get broker "+Thread.currentThread());
					
					//do something
					Thread.sleep(rd.nextInt(5000));
					
					try {
						assertEquals(broker, db.get(null));
	
						//do something
						Thread.sleep(rd.nextInt(5000));
					} finally {
						db.release(broker);
					}

				} finally {
			        System.out.println("releasing broker "+Thread.currentThread());
					db.release(broker);
				}
				
			} catch (Throwable e) {
				ex = e;
			}
		}
	}
}