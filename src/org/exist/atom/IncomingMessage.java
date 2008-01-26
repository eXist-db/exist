/*
 * IncomingMimeMessage.java
 *
 * Created on June 14, 2006, 11:55 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 *
 * @author R. Alexander Milowski
 */
public interface IncomingMessage {
   String getMethod();
   String getPath();
   String getHeader(String key);
   String getParameter(String name);
   InputStream getInputStream()
      throws IOException;
   int getContentLength();
   Reader getReader()
      throws IOException;
   String getModuleBase();
   
   HttpServletRequest getRequest();
}
