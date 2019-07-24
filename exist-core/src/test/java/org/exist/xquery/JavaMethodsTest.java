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

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class JavaMethodsTest {

	public static String echo(String arg0, String arg1) {
		return arg0 + " " + arg1;
	}
	
	public static String echo(String arg0, boolean bool) {
		return arg0 + " is " + bool;
	}
	
	public static int add(int a, int b) {
		return a + b;
	}
	
	public static double add(double a, double b) {
		return a + b;
	}
	
	private String message;
	
	public JavaMethodsTest(String msg) {
		message = msg;
	}
	
	public String display(String welcome) {
		return welcome + ": " + message;
	}
}
