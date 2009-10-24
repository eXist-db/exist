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

import org.exist.debuggee.CommandContinuation;
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
			debugger = new DebuggerImpl();

			DebuggingSource source = debugger.init("http://127.0.0.1:8080/exist/admin/admin.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			try { //why???
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			source.stepInto(this);
			source.stepOver(this);
			source.stepOut(this);

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
