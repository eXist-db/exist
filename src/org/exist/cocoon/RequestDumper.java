/*
 * RequestDumper.java - Jul 24, 2003
 * 
 * @author wolf
 */
package org.exist.cocoon;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestDumper extends HttpServlet {

	public RequestDumper() {
		super();
	}
	
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException {
		ServletOutputStream os = response.getOutputStream();
		response.setContentType("text/html; charset=UTF-8");
		Enumeration params = request.getParameterNames();
		String key;
		os.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		os.print("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
		os.print("<html><body>");
		os.print("<p>Encoding: ");
		os.print(request.getCharacterEncoding());
		os.print("</p><table border='0'>");
		while(params.hasMoreElements()) {
			key = (String)params.nextElement();
			os.print("<tr><td>");
			os.print(key);
			os.print("</td><td>");
			String values[] = request.getParameterValues(key);
			for(int i = 0; i < values.length; i++) {
				if(i > 0)
					os.print(",");
				os.print(values[i]);
			}
			os.print("</td></tr>");
		}
		os.print("</table></body></html>");
	}

}
