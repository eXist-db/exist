/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-10 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.management.client;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;

/**
 * A servlet to monitor the database. It returns status information for the database based
 * on the JMX interface. For simplicity, the JMX beans provided by eXist
 * are organized into categories. One calls the servlet with one or more
 * categories in parameter "c", e.g.:
 * 
 * /exist/jmx?c=instances&c=memory 
 * 
 * If no parameter is specified, all categories will be returned. Valid
 * categories are "memory", "instances", "disk", "system", "caches",
 * "locking", "processes", "sanity", "all".
 * 
 * The servlet can also be used to test if the database is responsive by
 * using parameter "operation=ping" and a timeout (t=timeout-in-milliseconds).
 * For example, the following call
 * 
 * /exist/jmx?operation=ping&t=1000
 * 
 * will wait for a response within 1000ms. If the ping returns within the
 * specified timeout, the servlet returns the attributes of the SanityReport
 * JMX bean, which will include an element &lt;jmx:Status&gt;PING_OK&lt;/jmx:Status&gt;.
 * If the ping takes longer than the timeout, you'll instead find an element  
 * &lt;jmx:error&gt; in the returned XML. In this case, additional information on
 * running queries, memory consumption and database locks will be provided.
 * 
 * @author wolf
 *
 */
public class JMXServlet extends HttpServlet {

	private final static Properties defaultProperties = new Properties();
	static {
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
	}
	
	private JMXtoXML client;

	public JMXServlet() {
	}

	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Element root = null;
		
		String operation = req.getParameter("operation");
		if (operation != null && "ping".equals(operation)) {
			long timeout = 5000;
			String timeoutParam = req.getParameter("t");
			if (timeoutParam != null) {
				try {
					timeout = Long.parseLong(timeoutParam);
				} catch (NumberFormatException e) {
					throw new ServletException("timeout parameter needs to be a number. Got: " + timeoutParam);
				}
			}
			long responseTime = client.ping("exist", timeout);
			if (responseTime == JMXtoXML.PING_TIMEOUT)
				root = client.generateXMLReport("no response on ping after " + timeout + "ms", 
						new String[] { "sanity", "locking", "processes", "instances", "memory" });
			else
				root = client.generateXMLReport(null, new String[] { "sanity" });
		} else {
			String[] categories = req.getParameterValues("c");
			if (categories == null)
				categories = new String[] { "all" };
			root = client.generateXMLReport(null, categories);
		}
		
		resp.setContentType("application/xml");
		
		Object useAttribute = req.getAttribute("jmx.attribute");
		if (useAttribute != null)
			req.setAttribute(useAttribute.toString(), root);
		else {
			Writer writer = new OutputStreamWriter(resp.getOutputStream(), "UTF-8");
			DOMSerializer streamer = new DOMSerializer(writer, defaultProperties);
			try {
				streamer.serialize(root);
			} catch (TransformerException e) {
				throw new ServletException("Error while serializing result: " + e.getMessage(), e);
			}
			writer.flush();
		}
	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		client = new JMXtoXML();
		client.connect();
	}
}
