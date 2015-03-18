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
public class CreateBinaryLoggable extends AbstractLoggable {

   protected final static Logger LOG = LogManager.getLogger(RenameBinaryLoggable.class);
   
   DBBroker broker;
   File original;
   
   /**
    * Creates a new instance of RenameBinaryLoggable
    */
   public CreateBinaryLoggable(DBBroker broker,Txn txn,File original)
   {
      super(NativeBroker.LOG_CREATE_BINARY,txn.getId());
      this.broker = broker;
      this.original = original;
   }
   
   public CreateBinaryLoggable(DBBroker broker,long transactionId) {
      super(NativeBroker.LOG_CREATE_BINARY,transactionId);
      this.broker = broker;
   }
   
   /* (non-Javadoc)
    * @see org.exist.storage.log.Loggable#write(java.nio.ByteBuffer)
    */
   public void write(ByteBuffer out) {
      final String originalPath = original.getAbsolutePath();
      final byte [] data = originalPath.getBytes();
      out.putInt(data.length);
      out.put(data);
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#read(java.nio.ByteBuffer)
     */
    public void read(ByteBuffer in) {
       final int size = in.getInt();
       final byte [] data = new byte[size];
       in.get(data);
       original = new File(new String(data));
    }

    /* (non-Javadoc)
     * @see org.exist.storage.log.Loggable#getLogSize()
     */
    public int getLogSize() {
        return 4 + original.getAbsolutePath().getBytes().length;
    }

    public void redo() throws LogException {
       // TODO: do we need to redo?  The file was stored...
    }
    
    public void undo() throws LogException {
       if (!original.delete()) {
          throw new LogException("Cannot delete binary resource "+original);
       }
    }
    
     public String dump() {
        return super.dump() + " - create binary "+original;
    }

}
