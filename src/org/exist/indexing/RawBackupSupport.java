package org.exist.indexing;

import org.exist.backup.RawDataBackup;

import java.io.IOException;

/**
 * Interface to be implemented by an index if it wants to add raw data
 * files to a raw system backup. This features is mainly used by the
 * {@link org.exist.storage.DataBackup} system task.
 */
public interface RawBackupSupport {

    void backupToArchive(RawDataBackup backup) throws IOException;
}
