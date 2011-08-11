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
