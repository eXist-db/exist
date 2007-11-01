/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Nov 1, 2007
 * Time: 6:51:30 PM
 * To change this template use File | Settings | File Templates.
 */
package org.exist.backup;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Callback interface, mainly used by the {@link org.exist.storage.DataBackup}
 * system task to write the raw data files to an archive..
 */
public interface RawDataBackup {

    public OutputStream newEntry(String name) throws IOException;

    public void closeEntry() throws IOException;
}