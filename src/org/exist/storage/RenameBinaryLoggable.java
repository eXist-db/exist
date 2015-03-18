/*
 * RenameBinaryLoggable.java
 *
 * Created on December 9, 2007, 1:57 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.exist.storage;

import java.io.File;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 *
 * @author alex
 */
public class RenameBinaryLoggable extends AbstractLoggable {

   protected final static Logger LOG = LogManager.getLogger(RenameBinaryLoggable.class);
   
   DBBroker broker;
   File original;
   File backup;
   /**
    * Creates a new instance of RenameBinaryLoggable
    */
   public RenameBinaryLoggable(DBBroker broker,Txn txn,File original,File backup)
   {
      super(NativeBroker.LOG_RENAME_BINARY,txn.getId());
      this.broker = broker;
      this.original = original;
      this.backup = backup;
      LOG.debug("Rename binary created "+original+" -> "+backup);
   }
   
   public RenameBinaryLoggable(DBBroker broker,long transactionId) {
      super(NativeBroker.LOG_RENAME_BINARY,transactionId);
      this.broker = broker;
      LOG.debug("Rename binary created ...");
   }
   
   /* (non-Javadoc)
    * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
    */
   public void write(ByteBuffer out) {
      final String originalPath = original.getAbsolutePath();
      byte [] data = originalPath.getBytes();
      out.putInt(data.length);
      out.put(data);
      final String backupPath = backup.getAbsolutePath();
      data = backupPath.getBytes();
      out.putInt(data.length);
      out.put(data);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
       int size = in.getInt();
       byte [] data = new byte[size];
       in.get(data);
       original = new File(new String(data));
       size = in.getInt();
       data = new byte[size];
       in.get(data);
       backup = new File(new String(data));
       LOG.debug("Rename binary read: "+original+" -> "+backup);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 8 + original.getAbsolutePath().getBytes().length + backup.getAbsolutePath().getBytes().length;
    }

    public void redo() throws LogException {
    }
    
    public void undo() throws LogException {
       LOG.debug("Undo rename: "+original);
       if (!backup.renameTo(original)) {
          throw new LogException("Cannot move original "+original+" to backup file "+backup);
       }
    }
    
     public String dump() {
        return super.dump() + " - rename "+original+" to "+backup;
    }

}
