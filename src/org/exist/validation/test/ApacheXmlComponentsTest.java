/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

package org.exist.validation.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.exist.validation.Validator;
import org.exist.validation.XmlLibraryChecker;

/**
 *  Class for testing xerces and xalan configuration.
 *
 * @author dizzzz
 */
public class ApacheXmlComponentsTest extends TestCase {  
        
    public ApacheXmlComponentsTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(ApacheXmlComponentsTest.class);
        
        return suite;
    }
    
    protected void tearDown() {       
        System.out.println("tearDown");
    }
    
    protected void setUp() {      
        System.out.println("setUp");
    }
    
    
     public void testXercesVersion() {

         String version = org.apache.xerces.impl.Version.getVersion();
         
         System.out.println("Xerces");
         System.out.println("Required version '"+XmlLibraryChecker.XERCESVERSION+"'");
         System.out.println("Found version '"+version+"'");
         
         assertTrue("Incorrect Xerces version! "+
                             "Please put correct jar in endorsed folder",
                             XmlLibraryChecker.isXercesVersionOK() );
         
     }
     
     public void testXalanVersion() {
         
         String version = org.apache.xalan.Version.getVersion();
         
         System.out.println("Xalan");
         System.out.println("Required version '"+ XmlLibraryChecker.XALANVERSION+"'");
         System.out.println("Found version '"+version+"'");
         
         assertTrue("Incorrect Xalan version! "+
                             "Please put correct jar in endorsed folder", 
                             XmlLibraryChecker.isXalanVersionOK() );
     }
}
