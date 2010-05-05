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
package org.exist.versioning.svn.internal.wc.admin;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNProperties14 extends SVNVersionedProperties {

    private SVNAdminArea14 myAdminArea;
    private String myEntryName;
    
    public SVNProperties14(SVNProperties props, SVNAdminArea14 adminArea, String entryName) {
        super(props);
        myAdminArea = adminArea;
        myEntryName = entryName;
    }

    public boolean containsProperty(String name) throws SVNException {
        SVNProperties propsMap = getProperties();
        if (propsMap != null && propsMap.containsName(name)) {
            return true;
        }

        SVNEntry entry = myAdminArea.getEntry(myEntryName, true);
        if (entry == null) {
            return false;
        }
        String[] cachableProps = entry.getCachableProperties(); 
        if (cachableProps != null && getIndex(cachableProps, name) >= 0) {
            String[] presentProps = entry.getPresentProperties();
            if (presentProps == null || getIndex(presentProps, name) < 0) {
                return false;
            }
            return true;
        }
        if (!isEmpty()) {
            SVNProperties props = loadProperties();
            return props.containsName(name);
        }
        return false;
    }

    public SVNPropertyValue getPropertyValue(String name) throws SVNException {
        if (getProperties() != null && getProperties().containsName(name)) {
            return getProperties().getSVNPropertyValue(name);
        }

        SVNEntry entry = myAdminArea.getEntry(myEntryName, true);
        if (entry != null) {
            String[] cachableProps = entry.getCachableProperties(); 
            if (cachableProps != null && getIndex(cachableProps, name) >= 0) {
                String[] presentProps = entry.getPresentProperties();
                if (presentProps == null || getIndex(presentProps, name) < 0) {
                    return null;
                }
                if (SVNProperty.isBooleanProperty(name)) {
                    return SVNProperty.getValueOfBooleanProperty(name);
                }
            }
        }
        
        SVNProperties props = loadProperties();
        if (!isEmpty()) {
            return props.getSVNPropertyValue(name); 
        }
        return null;
    }

    //TODO: this is not a good approach, however don't want to
    //sort the original array to use it with Arrays.binarySearch(), 
    //since there's a risk to lose the order elements are stored in
    //the array and written to the file. Maybe the storage order is 
    //important for somewhat somewhere... 
    private int getIndex(String[] array, String element) {
        if (array == null || element == null) {
            return -1;
        }
        for(int i = 0; i < array.length; i++){
            if (element.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }

    protected SVNVersionedProperties wrap(SVNProperties properties) {
        return new SVNProperties13(properties);
    }

}
