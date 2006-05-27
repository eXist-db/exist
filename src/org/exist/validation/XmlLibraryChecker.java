/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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

package org.exist.validation;

/**
 *  Class for checking dependencies with XML libraries.
 *
 * @author dizzzz
 */
public class XmlLibraryChecker {
    
    // Required Xerces version.
    public static final String XERCESVERSION = "Xerces-J 2.8.0";
    
    // Required Xalan version.
    public static final String XALANVERSION  = "Xalan Java 2.7.0";
    
    public static final String UNKNOWNVERSION = "<No version>";
    
    public static String getXercesVersion(){
        
        String version = "Xerces version could not be determined";
        try{
            version = org.apache.xerces.impl.Version.getVersion();
        } catch (Exception ex) {
            System.out.println(version + "; '" + ex.getMessage() +"'");
        }
        return version;
    }
    
    public static String getXalanVersion(){
        String version = "Xalan version could not be determined";
        try{
            version = org.apache.xalan.Version.getVersion();
        } catch (Exception ex) {
            System.out.println(version + "; '" + ex.getMessage() +"'");
        }
        return version;
    }
    
    public static boolean isXercesVersionOK(){
        return ( XERCESVERSION.equals(getXercesVersion()) );
    }
    
    public static boolean isXalanVersionOK(){
        return ( XALANVERSION.equals(getXalanVersion()) );
    }
    
}
