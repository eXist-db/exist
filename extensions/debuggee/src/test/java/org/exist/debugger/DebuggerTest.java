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
 *  $Id$
 */
package org.exist.debugger;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.CommandContinuation;
import org.exist.debugger.model.Breakpoint;
import org.exist.debugger.model.Location;
import org.exist.debugger.model.Variable;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistWebServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@Ignore
public class DebuggerTest implements ResponseListener {

	private static final String script = "xquery version '1.0';\n" +
			"(: computes the first 10 fibonacci numbers :)\n" +
			"\n" +
			"declare namespace f='http://exist-db.org/NS/fibo';\n" +
			"\n" +
			"declare function f:fibo($n as xs:integer) as item() {\n" +
			"	if ($n = 0)\n" +
			"	then 0\n" +
			"	else if ($n = 1)\n" +
			"	then 1\n" +
			"	else (f:fibo($n - 1) + f:fibo($n -2))\n" +
			"};\n" +
			"\n" +
			"for $n in 1 to 10\n" +
			"	return\n" +
			"		<tr>\n" +
			"			<td>{$n}</td>\n" +
			"			<td>{f:fibo($n)}</td>\n" +
			"		</tr>";

	@ClassRule
	public static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

	@BeforeClass
	public static void setup() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
		store("fibo.xql", script);
	}

	@Test
	public void testConnection() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();

		Exception exception = null;
		
		//if resource don't exist throw exception
		try {
		    // jetty.port.jetty
			debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/xquery/fibo.xql");
			assertNotNull("The resource don't exist, but debugger don't throw exception.", null);
		} catch (Exception e) {
			exception = e;
		}
		
		assertEquals(exception.getClass().toString(), "class java.io.IOException");
		
		try {
		    // jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/fibo.xql");
			assertNotNull("Debugging source can't be NULL.", source);
            source.stop();
		} catch (Exception e) {
			assertNotNull("exception: "+e.getMessage(), null);
		}
	}

	@Test
	public void testDebugger() {
		Debugger debugger;
		
		try {
			debugger = DebuggerImpl.getDebugger();

			//sending init request
			// jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/fibo.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			//get stack frames
			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(15, stack.get(0).getLineBegin());

			//sending step-into
			source.stepInto(this);
			
			try {
				Thread.sleep(1000); //TODO: query current stage or wait for BREAK status ???
			} catch (InterruptedException e) {
			}

			//"get stack frames
			stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(16, stack.get(0).getLineBegin());

			for (int i = 0; i < 9; i++) {
				source.stepInto(this);
			}
			try {
				Thread.sleep(5000); //TODO: query current stage or wait for BREAK status ???
			} catch (InterruptedException e) {
			}
			
			//get stack frames
			stack = source.getStackFrames();
			assertEquals(3, stack.size());
			assertEquals(8, stack.get(0).getLineBegin());
			
			assertEquals(24, stack.get(1).getLineBegin());
			assertEquals(78, stack.get(1).getColumnBegin());

			assertEquals(24, stack.get(2).getLineBegin());
			assertEquals(42, stack.get(2).getColumnBegin());
			
			//sending get-variables first time
			List<Variable> vars = source.getVariables();
			
			assertEquals(2, vars.size());
			
			for (Variable var : vars) {
				if (var.getName().equals("n"))
					assertEquals("1", var.getValue());
				else if (var.getName().equals("dbgp:session"))
					assertEquals("default", var.getValue());
			}
			
			//sending get-local-variables
			vars = source.getLocalVariables();
			assertEquals(1, vars.size());
			for (Variable var : vars) {
				assertEquals("n", var.getName());
				assertEquals("1", var.getValue());
			}

			//sending get-glocal-variables
			vars = source.getGlobalVariables();
			assertEquals(1, vars.size());
			for (Variable var : vars) {
				assertEquals("DBGp:session", var.getName());
				assertEquals("default", var.getValue());
			}

			//sending step-into & waiting stop status
			for (int i = 0; i < 7; i++) {
				source.stepInto();
			}

			//sending get-variables second time
			vars = source.getVariables();
			
			assertEquals(2, vars.size());
			
			for (Variable var : vars) {
				if (var.getName().equals("n"))
					assertEquals("2", var.getValue());
				else if (var.getName().equals("dbgp:session"))
					assertEquals("default", var.getValue());
			}

			//sending step-over
			source.stepOver(this);

			//sending step-out
			source.stepOut(this);

			//sending run
			source.run(this);

		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	@Test
	public void testBreakpoints() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();
		
		try {
			// jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/fibo.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			Breakpoint breakpoint = source.newBreakpoint();
			breakpoint.setLineno(24);
			breakpoint.sync();
			
			source.run();
			
			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(24, stack.get(0).getLineBegin());
			
			breakpoint.remove();

			source.run();
			
		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	@Test
	public void testLineBreakpoint() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();
		
		try {
			//sending init request
			// jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/fibo.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			Breakpoint breakpoint = source.newBreakpoint();
			breakpoint.setLineno(24);
			breakpoint.sync();
			
			source.run();
			
			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(24, stack.get(0).getLineBegin());
			
			source.stepInto();

			stack = source.getStackFrames();
			assertEquals(3, stack.size());
			assertEquals(8, stack.get(0).getLineBegin());

			source.stop();
			
		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	@Test
	public void testEvaluation() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();
		
		try {
			//sending init request
			// jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/fibo.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			Breakpoint breakpoint = source.newBreakpoint();
			breakpoint.setLineno(24);
			breakpoint.sync();
			
			String res = source.evaluate("$dbgp:session");
			assertNull(res);

			res = source.evaluate("let $seq := (98.5, 98.3, 98.9) return count($seq)");
			assertEquals("3", res);

			//xquery engine have problem here, because context not copied correctly
//			res = source.evaluate("f:fibo(2)");
//			System.out.println(res);
			
			source.run();
			
			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(24, stack.get(0).getLineBegin());
			
			breakpoint.remove();

			source.stop();
			
		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

    @Test
	public void testEvaluation2() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();

		try {
			//sending init request
			// jetty.port.jetty
			DebuggingSource source = debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/debug-test.xql");

			assertNotNull("Debugging source can't be NULL.", source);

			Breakpoint breakpoint = source.newBreakpoint();
			breakpoint.setLineno(19);
			breakpoint.sync();

			source.run();

			List<Location> stack = source.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(19, stack.get(0).getLineBegin());

            String res = source.evaluate("$t:XML");
			assertNotNull(res);
            //$t:XML: " + res
            assertEquals("<root><a id=\"a1\"/><b id=\"b1\" type=\"t\"/><c id=\"c1\">text</c><d id=\"d1\"><e>text</e></d></root>", res);
			breakpoint.remove();

			source.stop();

		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	@Test
	public void testResourceNotExistOrNotRunnable() throws IOException {
		Debugger debugger = DebuggerImpl.getDebugger();
		
		Exception exception = null;
		
		try {
			//sending init request
			// jetty.port.jetty
			debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/exist/logo.jpg");

			fail("This point should not be reached");

		} catch (IOException e) {
			exception = e;
		} catch (ExceptionTimeout e) {
			exception = e;
		}
		assertEquals(exception.getClass().toString(), "class java.io.IOException");

		try {
			//sending init request
			// jetty.port.jetty
			debugger.init("http://127.0.0.1:" + existWebServer.getPort() + "/notExist/fibo.xql");

			fail("This point should not be reached");

		} catch (IOException e) {
			exception = e;
		} catch (ExceptionTimeout e) {
			exception = e;
		}
		assertEquals(exception.getClass().toString(), "class java.io.IOException");
	}

	@Test
	public void testStepInto() throws Exception {
	    // jetty.port.jetty
		String url = "http://127.0.0.1:" + existWebServer.getPort() + "/exist/xquery/json-test.xql";
		for (int i = 0; i < 10; i++) {
			Debugger debugger = DebuggerImpl.getDebugger();
			DebuggingSource debuggerSource = debugger.init(url);

			//send stepInto
			debuggerSource.stepInto();
			//Thread.sleep(1000);

			//send getStackFrames
			List<Location> stack = debuggerSource.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(8, stack.get(0).getLineBegin());
			assertEquals(6, stack.get(0).getColumnBegin());

			//send stop
			debuggerSource.stop();
			//Thread.sleep(1000);

			DebuggerImpl.shutdownDebugger();
		} 
	}

	@Test
	public void testStoredInDB() throws Exception {
		store("script.xql", script);
		// jetty.port.jetty
		String url = "http://127.0.0.1:" + existWebServer.getPort() + "/exist/rest/db/test/script.xql";
		
		for (int i = 0; i < 10; i++) {
			Debugger debugger = DebuggerImpl.getDebugger();
			DebuggingSource debuggerSource = debugger.init(url);

			//send stepInto
			debuggerSource.stepInto();
			//Thread.sleep(1000);

			//send getStackFrames
			List<Location> stack = debuggerSource.getStackFrames();
			assertEquals(1, stack.size());
			assertEquals(16, stack.get(0).getLineBegin());
			assertEquals(18, stack.get(0).getColumnBegin());

			//send stop
			debuggerSource.stop();
			//Thread.sleep(1000);
			
			//stoped

			DebuggerImpl.shutdownDebugger();
		} 
	}

	public void responseEvent(CommandContinuation command, Response response) {
		//System.out.println("getResponse command = "+command);
	}
	
    private static void store(String name,  String data) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
    	final Database pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

			Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
			broker.saveCollection(transaction, root);
			assertNotNull(root);

			root.addBinaryResource(transaction, broker, XmldbURI.create(name), data.getBytes(), "application/xquery");

			transact.commit(transaction);
		}
    }

}
