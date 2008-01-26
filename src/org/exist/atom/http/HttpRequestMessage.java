/*
 * HttpRequestMIMEMessage.java
 *
 * Created on June 16, 2006, 12:09 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.http;

import org.exist.atom.IncomingMessage;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 *
 * @author R. Alexander Milowski
 */
public class HttpRequestMessage implements IncomingMessage {

   String path;
   String base;
   HttpServletRequest request;
   /** Creates a new instance of HttpRequestMIMEMessage */
   public HttpRequestMessage(HttpServletRequest request,String path,String base) {
      this.request = request;
      this.path = path;
      this.base = base;
   }
   
   public String getMethod() {
      return request.getMethod();
   }
   public String getPath() {
      return path;
   }
   public String getParameter(String name) {
      return request.getParameter(name);
   }
   
   public String getHeader(String key) {
      return request.getHeader(key);
   }

   public int getContentLength() {
      return request.getContentLength();
   }
   
   public InputStream getInputStream() 
      throws IOException
   {
      return request.getInputStream();
   }   

   public Reader getReader() 
      throws IOException
   {
      return request.getReader();
   }
   
   public String getModuleBase() {
      return base;
   }
   
   public HttpServletRequest getRequest()
   {
	   return request;
   }
}
