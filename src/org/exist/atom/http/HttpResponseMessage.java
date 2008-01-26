/*
 * HttpRequestMIMEMessage.java
 *
 * Created on June 16, 2006, 12:09 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.http;

import org.exist.atom.OutgoingMessage;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 *
 * @author R. Alexander Milowski
 */
public class HttpResponseMessage implements OutgoingMessage {

   HttpServletResponse response;
   /** Creates a new instance of HttpRequestMIMEMessage */
   public HttpResponseMessage(HttpServletResponse response) {
      this.response = response;
   }
   public void setStatusCode(int code) {
      response.setStatus(code);
   }
   
   public void setContentType(String value) {
      response.setContentType(value);
   }
   
   public void setHeader(String key,String value) {
      response.setHeader(key,value);
   }
   
   public OutputStream getOutputStream() 
      throws IOException
   {
      return response.getOutputStream();
   }
   public Writer getWriter() 
      throws IOException
   {
      return response.getWriter();
   }
   
   public HttpServletResponse getResponse()
   {
	   return response;
   }
}
