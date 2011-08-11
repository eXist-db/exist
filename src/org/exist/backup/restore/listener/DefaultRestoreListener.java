package org.exist.backup.restore.listener;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class DefaultRestoreListener extends AbstractRestoreListener {
     
    @Override
    public void info(String message) {
        System.err.println(message);
    }

    @Override
    public void warn(String message) {
        super.warn(message);

        System.err.println(message);
    }

    @Override
    public void error(String message) {
        super.error(message);

        System.err.println(message);
    }
}