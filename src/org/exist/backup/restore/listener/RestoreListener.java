/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2011 The eXist-db Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id: Restore.java 15109 2011-08-09 13:03:09Z deliriumsky $
 */
package org.exist.backup.restore.listener;

import java.util.Observable;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public interface RestoreListener {
    
    void createCollection(String collection);
    
    void restored(String resource);
    
    void info(String message);
    
    void warn(String message);
    
    void error(String message);
    
    String warningsAndErrorsAsString();

    boolean hasProblems();

    public void setCurrentCollection(String currentCollectionName);

    public void setCurrentResource(String currentResourceName);
    
    public void restoreStarting();

    public void restoreFinished();

    public void observe(Observable observable);

    public void setCurrentBackup(String currentBackup);
}