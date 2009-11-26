/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery;

import junit.framework.TestCase;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryUseCasesTest extends TestCase {

	public static void main(String[] args) {
		junit.textui.TestRunner.run(XQueryUseCasesTest.class);
	}

	protected XQueryUseCase useCase = new XQueryUseCase();

	public XQueryUseCasesTest(String name) {
		super(name);
	}
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		useCase.setUp();
	}

	// jmv: to activate when we'll have function deep-equal()
//	public void testXMP() throws Exception {
//		useCase.doTest("xmp");
//	}
	
	public void testSGML() throws Exception {
		useCase.doTest("sgml");
	}
	
	public void testTREE() throws Exception {
		useCase.doTest("tree");
	}
	
	public void testParts() throws Exception {
		useCase.doTest("parts");
	}
	
	public void testString() throws Exception {
		useCase.doTest("string");
	}
	
	public void testNS() throws Exception {
		useCase.doTest("ns");
	}
	
	public void testSeq() throws Exception {
		useCase.doTest("seq");
	}

	// jmv: to activate when implemented
	// org.xmldb.api.base.XMLDBException: Cannot query constructed nodes.
//	public void testR() throws Exception {
//		useCase.doTest("r");
//	}
}
