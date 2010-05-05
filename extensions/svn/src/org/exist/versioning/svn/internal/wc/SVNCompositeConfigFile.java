/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNCompositeConfigFile {
    
    private SVNConfigFile myUserFile;
    private SVNConfigFile mySystemFile;
    
    /**
     * Highest priority in-memory read-only options.
     */
    private Map myGroupsToOptions;
    
    public SVNCompositeConfigFile(SVNConfigFile systemFile, SVNConfigFile userFile) {
        mySystemFile = systemFile;
        myUserFile = userFile;
    }
    
    public Map getProperties(String groupName) {
        Map system = mySystemFile.getProperties(groupName);
        Map user = myUserFile.getProperties(groupName);
        system.putAll(user);
        
        if (myGroupsToOptions != null) {
            Map groupOptions = (Map) myGroupsToOptions.get(groupName);
            if (groupOptions != null) {
                system.putAll(groupOptions);
            }
        }
        return system;
    }

    public void setGroupsToOptions(Map groupToOptions) {
        myGroupsToOptions = groupToOptions;
    }
    
    public String getPropertyValue(String groupName, String propertyName) {
        String value = null;
        if (myGroupsToOptions != null) {
            Map groupOptions = (Map) myGroupsToOptions.get(groupName);
            if (groupOptions != null) {
                value = (String) groupOptions.get(propertyName);
            }
        }
        
        if (value == null) {
            value = myUserFile.getPropertyValue(groupName, propertyName);    
        }
        
        if (value == null) {
            value = mySystemFile.getPropertyValue(groupName, propertyName);
        }
        return value;
    }

    public void setPropertyValue(String groupName, String propertyName, String propertyValue, boolean save) {
        myUserFile.setPropertyValue(groupName, propertyName, propertyValue, save);
    }
    
    public boolean isModfied() {
        return myUserFile.isModified();
    }

    public void save() {
        myUserFile.save();
    }
}
