/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;

/**
 * <b>SVNPropertyData</b> is a wrapper for both versioned and unversioned
 * properties. This class represents the pair: property name - property value.
 * Property managing methods of the <b>SVNWCClient</b> class use 
 * <b>SVNPropertyData</b> to wrap properties and dispatch them to 
 * <b>handleProperty()</b> methods of <b>ISVNPropertyHandler</b> for processing
 * or simply return that 'properties object' as a target.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNPropertyHandler
 * @see     SVNWCClient
 */
public class SVNPropertyData {

    private SVNPropertyValue myValue;

    private String myName;
    
    /**
     * Constructs an <b>SVNPropertyData</b> given a property name and its
     * value. 
     * 
     * <p>
     * if <code>data</code> is not <span class="javakeyword">null</span>, is a 
     * {@link SVNPropertyValue#isString() string} property and <code>name</code> is an 
     * {@link SVNProperty#isSVNProperty(String) svn-namespace} property name, then <code>options</code>, 
     * if not <span class="javakeyword">null</span>, is used to translate the property value replacing 
     * all LF end of line markers in the property value with ones returned by {@link ISVNOptions#getNativeEOL()}.
     * Otherwise, if <code>options</code> is <span class="javakeyword">null</span>, 
     * the <span class="javastring">"line.separator"</span> system property is used to retrieve a new EOL marker.
     *  
     * @param name    a property name
     * @param data    a property value
     * @param options provides EOL style information
     */
    public SVNPropertyData(String name, SVNPropertyValue data, ISVNOptions options) {
        myName = name;
        myValue = data;
        if (myValue != null && SVNProperty.isSVNProperty(myName) && myValue.isString()) {
            String nativeEOL = options == null ? System.getProperty("line.separator") : new String(options.getNativeEOL());
            myValue = SVNPropertyValue.create(myValue.getString().replaceAll("\n", nativeEOL));
        }
    }
    
    /**
     * Gets the name of the property represented by this 
     * <b>SVNPropertyData</b> object. 
     * 
     * @return  a property name
     */
    public String getName() {
        return myName;
    }
    
    /**
     * Gets the value of the property represented by this 
     * <b>SVNPropertyData</b> object.
     *  
     * @return  a property value
     */
    public SVNPropertyValue getValue() {
        return myValue;
    }

    public int hashCode() {
        int result = 17 + ((myName == null) ? 0 : myName.hashCode());
        return 31 * result + ((myValue == null) ? 0 : myValue.hashCode());
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }         
            
        SVNPropertyData other = (SVNPropertyData) obj;
        if (myName == null) {
            if (other.myName != null) {
                return false;
            }
        } else if (!myName.equals(other.myName)) {
            return false;
        }
        if (myValue == null) {
            if (other.myValue != null) {
                return false;
            }
        } else if (!myValue.equals(other.myValue)) {
            return false;
        }
        return true;
    }

}
