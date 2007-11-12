/*
 * AtomModule.java
 *
 * Created on June 14, 2006, 11:06 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom;

import java.io.IOException;
import java.net.URL;

import org.exist.EXistException;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 *
 * @author R. Alexander Milowski
 */
public interface AtomModule {
   public interface Context {
      String getDefaultCharset();
      String getParameter(String name);
      String getContextPath();
      URL getContextURL();
      String getModuleLoadPath();
   }
   void init(Context context) throws EXistException;
   void process(DBBroker broker,IncomingMessage message,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException,IOException;
}
