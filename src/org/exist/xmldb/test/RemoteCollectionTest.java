/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb.test;

import junit.framework.TestCase;
import org.exist.Server;

/** WORK IN PROGRESS !!!
 * @author jmv
 */
public class RemoteCollectionTest extends TestCase {
    protected final static String URI = "http://localhost:8080/exist/xmlrpc";

	/** ? @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		String[] args = {"standalone"};
		Server.main(args);
		// Thread ??
		
	}
	/** ? @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		// TODO 
	}
	public void testIndexQueryService() {
		// TODO .............
	}
	/**
	 * @param name
	 */
	public RemoteCollectionTest(String name) {
		super(name);
	}

}
