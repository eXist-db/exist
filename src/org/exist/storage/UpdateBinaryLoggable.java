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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
public class UpdateBinaryLoggable extends AbstractLoggable {

   protected final static Logger LOG = LogManager.getLogger(RenameBinaryLoggable.class);
   
   DBBroker broker;
   File original;
   File backup;
   /**
    * Creates a new instance of RenameBinaryLoggable
    */
   public UpdateBinaryLoggable(DBBroker broker,Txn txn,File original,File backup)
   {
      super(NativeBroker.LOG_UPDATE_BINARY,txn.getId());
      this.broker = broker;
      this.original = original;
      this.backup = backup;
   }
   
   public UpdateBinaryLoggable(DBBroker broker,long transactionId) {
      super(NativeBroker.LOG_UPDATE_BINARY,transactionId);
      this.broker = broker;
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
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 8 + original.getAbsolutePath().getBytes().length + backup.getAbsolutePath().getBytes().length;
    }

    public void redo() throws LogException {
       // TODO: is there something to do?  The file has been written
    }
    
    public void undo() throws LogException {
       try {
          final FileInputStream is = new FileInputStream(backup);
          final FileOutputStream os = new FileOutputStream(original);
          final byte [] buffer = new byte[4096];
          int len;
          while ((len=is.read(buffer))>=0) {
             os.write(buffer,0,len);
          }
          os.close();
          is.close();
       } catch (final IOException ex) {
          
       }
    }
    
     public String dump() {
        return super.dump() + " - update "+original+" to "+backup;
    }

}
