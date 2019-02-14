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

import org.junit.Before;
import org.junit.Test;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryUseCasesTest {

	protected XQueryUseCase useCase = new XQueryUseCase();

	@Before
	public void setUp() throws Exception {
		useCase.setUp();
	}

	// jmv: to activate when we'll have function deep-equal()
//	public void testXMP() throws Exception {
//		useCase.doTest("xmp");
//	}

	@Test
	public void sgml() throws Exception {
		useCase.doTest("sgml");
	}

	@Test
	public void tree() throws Exception {
		useCase.doTest("tree");
	}

	@Test
	public void parts() throws Exception {
		useCase.doTest("parts");
	}

	@Test
	public void string() throws Exception {
		useCase.doTest("string");
	}

	@Test
	public void ns() throws Exception {
		useCase.doTest("ns");
	}

	@Test
	public void seq() throws Exception {
		useCase.doTest("seq");
	}

	// jmv: to activate when implemented
	// org.xmldb.api.base.XMLDBException: Cannot query constructed nodes.
//	@Test
//	public void r() throws Exception {
//		useCase.doTest("r");
//	}
}
