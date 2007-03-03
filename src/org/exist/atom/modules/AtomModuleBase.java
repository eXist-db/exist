/*
 * AtomModuleBase.java
 *
 * Created on June 16, 2006, 12:46 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.exist.EXistException;
import org.exist.atom.AtomModule;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 *
 * @author R. Alexander Milowski
 */
public class AtomModuleBase implements AtomModule {

   protected Context context;
   
   /** Creates a new instance of AtomModuleBase */
   public AtomModuleBase() {
   }
   
   public void init(Context context)
      throws EXistException
   {
      this.context = context;
   }
   
   protected Context getContext() {
      return context;
   }
   
   public void process(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException,IOException
   {
      String method = request.getMethod();
      if (method.equals("GET")) {
         doGet(broker,request,response);
      } else if (method.equals("POST")) {
         doPost(broker,request,response);
      } else if (method.equals("PUT")) {
         doPut(broker,request,response);
      } else if (method.equals("HEAD")) {
         doHead(broker,request,response);
      } else if (method.equals("DELETE")) {
         doDelete(broker,request,response);
      } else {
         throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
      }
   }
   
   public void doGet(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
   }
   public void doHead(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
   }
   public void doPost(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
   }
   public void doPut(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
   }
   public void doDelete(DBBroker broker,IncomingMessage request,OutgoingMessage response) 
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException, IOException
   {
      throw new BadRequestException("Method "+request.getMethod()+" is not supported by this module.");
   }
   
   protected File storeInTemporaryFile(InputStream is,int len)
      throws IOException
   {
      File tempFile = File.createTempFile("atom", ".tmp");
      OutputStream os = new FileOutputStream(tempFile);
      byte[] buffer = new byte[4096];
      int count, l = 0;
      do {
         count = is.read(buffer);
         if (count > 0) {
            os.write(buffer, 0, count);
         }
         l += count;
      } while ((len<0 && count>=0) || l < len);
      os.close();   
      return tempFile;
   }
}
