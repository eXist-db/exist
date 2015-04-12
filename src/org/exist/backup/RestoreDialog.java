/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.backup;

import org.exist.storage.ElementIndex;
import org.exist.util.ProgressIndicator;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;

import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;


public class RestoreDialog extends JDialog
{
    private static final long serialVersionUID  = 3773486348231766907L;

    JTextField                currentCollection;
    JTextField                currentBackup;
    JTextField                resource;
    JTextArea                 messages;
    JProgressBar              progress;

    Observer                  progressObserver  = new UploadProgressObserver();

    /**
     * Creates a new RestoreDialog object.
     *
     * @param   owner
     * @param   title
     * @param   modal
     *
     * @throws  HeadlessException
     */
    public RestoreDialog( Frame owner, String title, boolean modal ) throws HeadlessException
    {
        super( owner, title, modal );
        setupComponents();
        setSize( new Dimension( 350, 200 ) );
        pack();
    }

    private void setupComponents()
    {
        final GridBagLayout grid = new GridBagLayout();
        getContentPane().setLayout( grid );
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets( 5, 5, 5, 5 );

        JLabel label = new JLabel( "Backup:" );
        c.gridx  = 0;
        c.gridy  = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.NONE;
        grid.setConstraints( label, c );
        getContentPane().add( label );

        currentBackup = new JTextField( 50 );
        currentBackup.setEditable( false );
        c.gridx  = 1;
        c.gridy  = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        grid.setConstraints( currentBackup, c );
        getContentPane().add( currentBackup );

        label    = new JLabel( "Collection:" );
        c.gridx  = 0;
        c.gridy  = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.NONE;
        grid.setConstraints( label, c );
        getContentPane().add( label );

        currentCollection = new JTextField( 50 );
        currentCollection.setEditable( false );
        c.gridx  = 1;
        c.gridy  = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        grid.setConstraints( currentCollection, c );
        getContentPane().add( currentCollection );

        label    = new JLabel( "Restoring:" );
        c.gridx  = 0;
        c.gridy  = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.NONE;
        grid.setConstraints( label, c );
        getContentPane().add( label );

        resource = new JTextField( 40 );
        resource.setEditable( false );
        c.gridx  = 1;
        c.gridy  = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        grid.setConstraints( resource, c );
        getContentPane().add( resource );

        label    = new JLabel( "Progress:" );
        c.gridx  = 0;
        c.gridy  = 3;
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.NONE;
        grid.setConstraints( label, c );
        getContentPane().add( label );

        progress = new JProgressBar();
        progress.setStringPainted( true );
        progress.setString( "" );
        c.gridx  = 1;
        c.gridy  = 3;
        c.anchor = GridBagConstraints.EAST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        grid.setConstraints( progress, c );
        getContentPane().add( progress );

        messages = new JTextArea( 5, 50 );
        messages.setEditable( false );
        final JScrollPane scroll = new JScrollPane( messages, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
        scroll.setBorder( BorderFactory.createTitledBorder( "Messages" ) );
        c.gridx     = 0;
        c.gridy     = 4;
        c.gridwidth = 2;
        c.anchor    = GridBagConstraints.WEST;
        c.fill      = GridBagConstraints.HORIZONTAL;
        grid.setConstraints( scroll, c );
        getContentPane().add( scroll );
    }


    public void setBackup( String backup )
    {
        currentBackup.setText( backup );
    }


    public void setCollection( String collection )
    {
        currentCollection.setText( collection );
    }


    public void setResource( String current )
    {
        resource.setText( current );
    }


    public void displayMessage( String message )
    {
        messages.append( message + '\n' );
        messages.setCaretPosition( messages.getDocument().getLength() );
    }


    public Observer getObserver()
    {
        return( progressObserver );
    }

    class UploadProgressObserver implements Observer
    {
        public void update( Observable o, Object arg )
        {
            progress.setIndeterminate( false );
            final ProgressIndicator ind = (ProgressIndicator)arg;
            progress.setValue( ind.getPercentage() );

            if( o instanceof ElementIndex ) {
                progress.setString( "Storing elements" ); //$NON-NLS-1$
            } else {
                progress.setString( "Storing nodes" ); //$NON-NLS-1$
            }
        }

    }
}
