package org.exist;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.MalformedURLException;
import java.util.Vector;
import org.apache.xmlrpc.*;

/**
 * Simple Servlet to retrieve a document from the repository.
 *
 * It accepts the following parameters to a get-request:
 * - name      the document's name
 * - indent    if set to true, enables pretty printing of xml output
 * - encoding  set the character encoding of xml output
 */
public class GetServlet extends HttpServlet {
  
	protected String serverURI = "http://localhost:8081";
	protected XmlRpcClient client;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
	serverURI = config.getInitParameter("server");
	try {
		client = new XmlRpcClient(serverURI);
	} catch(MalformedURLException mue) {
		throw new ServletException("wrong url for init parameter \"server\"");
	}
  }
  
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    PrintWriter out = response.getWriter();
	String document = request.getParameter("name");
	String indent = request.getParameter("indent");
	String encoding = request.getParameter("encoding");
	if(document == null) {
		document = request.getRequestURI();
		int p;
		if((p = document.lastIndexOf('/')) >= 0)
			document = document.substring(p + 1);
	}
	try {
		response.setContentType("text/xml");
		Vector params = new Vector();
		params.addElement(document);
		if(indent != null && indent.equals("true"))
			params.addElement(new Integer(1));
        else
            params.addElement(new Integer(0));
		if(encoding != null)
			params.addElement(encoding);
		String xml = (String)client.execute("getDocument", params);
		out.println(xml);
	} catch(Exception e) {
		response.setContentType("text/html");
        out.println("<h1>eXist error</h1>");
        out.println("<p>The server responded with an exception</p>");
        out.println("<p>" + e.getMessage() + "</p>");
	}
	out.flush();
  }
}
