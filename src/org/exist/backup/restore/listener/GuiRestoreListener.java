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
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.exist.backup.RestoreDialog;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class GuiRestoreListener extends AbstractRestoreListener {

    private final RestoreDialog dialog;
    
    public GuiRestoreListener() {
       this(null);
    }
    
    public GuiRestoreListener(JFrame parent) {
        dialog = new RestoreDialog(parent, "Restoring data ...", false );
        dialog.setVisible(true);
    }
    
    @Override
    public void info(final String message) {
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.displayMessage(message);
            }
        });
    }

    @Override
    public void warn(final String message) {
        super.warn(message);

        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.displayMessage(message);
            }
        });
    }

    @Override
    public void error(final String message) {
        super.error(message);
     
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.displayMessage(message);
            }
        });
    }

    @Override
    public void observe(final Observable observable) {
        
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                observable.addObserver(dialog.getObserver());
            }
        });
    }

    @Override
    public void setCurrentBackup(final String currentBackup) {
        super.setCurrentBackup(currentBackup);
        
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.setBackup(currentBackup);
            }
        });
    }
    
    

    @Override
    public void setCurrentCollection(final String currentCollectionName) {
        super.setCurrentCollection(currentCollectionName);
        
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.setCollection(currentCollectionName);
            }
        });
    }
    
    @Override
    public void setCurrentResource(final String currentResourceName) {
        super.setCurrentResource(currentResourceName);

        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                dialog.setResource(currentResourceName);
            }
        });
    }
    
    public void hideDialog() {
        dialog.setVisible(false);
    }
}
