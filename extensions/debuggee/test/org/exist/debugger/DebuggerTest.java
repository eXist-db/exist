/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.debugger;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.exist.debuggee.CommandContinuation;
import org.exist.debugger.model.Location;
import org.exist.debugger.model.Variable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggerTest implements ResponseListener {
	
	@Test
	public void testDebugger() {
		assertNotNull("Database wasn't initilised.", database);
		
		Debugger debugger;
		
		try {
			System.out.println("creating debugger");
			debugger = new DebuggerImpl();

			System.out.println("sending init request");
			DebuggingSource source = debugger.init("http://127.0.0.1:8080/exist/xquery/fibo.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			try { //why???
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			System.out.println("get stack frames");
			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(15, stack.get(0).getLineBegin());

			System.out.print("sending step-into");
			System.out.print(".");
			source.stepInto(this);
			
			try {
				Thread.sleep(1000); //TODO: query current stage or wait for BREAK status ???
			} catch (InterruptedException e) {
			}

			System.out.println("get stack frames");
			stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(16, stack.get(0).getLineBegin());

			for (int i = 0; i < 9; i++) {
				System.out.print(".");
				source.stepInto(this);
			}
			try {
				Thread.sleep(5000); //TODO: query current stage or wait for BREAK status ???
			} catch (InterruptedException e) {
			}
			System.out.println("=");
			
			System.out.println("get stack frames");
			stack = source.getStackFrames();
			assertEquals(3, stack.size());
			assertEquals(8, stack.get(0).getLineBegin());
			assertEquals(24, stack.get(1).getLineBegin());
			assertEquals(24, stack.get(2).getLineBegin());
			
			System.out.println("sending get-variables first time");
			List<Variable> vars = source.getVariables();
			
			assertEquals(1, vars.size());
			
			for (Variable var : vars) {
				assertEquals("n", var.getName());
				assertEquals("1", var.getValue());
			}
			
			System.out.print("sending step-into & waiting stop status");
			for (int i = 0; i < 7; i++) {
				System.out.print(".");
				source.stepInto();
			}
			System.out.print("=");

			System.out.println("sending get-variables second time");
			vars = source.getVariables();
			
			assertEquals(1, vars.size());
			
			for (Variable var : vars) {
				assertEquals("n", var.getName());
				assertEquals("2", var.getValue());
			}

			System.out.println("sending step-over");
			source.stepOver(this);

			System.out.println("sending step-out");
			source.stepOut(this);

			System.out.println("sending run");
			source.run(this);

		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	static org.exist.start.Main database;

	@BeforeClass
    public static void initDB() {
		database = new org.exist.start.Main("jetty");
		database.run(new String[]{"jetty"});
    }

    @AfterClass
    public static void closeDB() {
       	database.shutdown();
    }

	public void responseEvent(CommandContinuation command, Response response) {
		System.out.println("getResponse command = "+command);
	}
}
