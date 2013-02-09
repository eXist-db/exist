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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.exist.dom.QName;
import org.exist.management.impl.SanityReport;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.ConfigurationHelper;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Utility class to output database status information from eXist's JMX interface
 * as XML.
 * 
 * @author wolf
 *
 */
public class JMXtoXML {

	private final static Logger LOG = Logger.getLogger(JMXtoXML.class);
	
	private final static Map<String, ObjectName[]> CATEGORIES = new TreeMap<String, ObjectName[]>();

    static {
        try {
            final ObjectName onMemory = new ObjectName("java.lang:type=Memory");
            final ObjectName onRuntime = new ObjectName("java.lang:type=Runtime");

            CATEGORIES.put("memory", new ObjectName[]{new ObjectName("java.lang:type=Memory")});
            CATEGORIES.put("runtime", new ObjectName[]{new ObjectName("java.lang:type=Runtime")});
            CATEGORIES.put("instances", new ObjectName[]{new ObjectName("org.exist.management.*:type=Database")});
            CATEGORIES.put("disk", new ObjectName[]{new ObjectName("org.exist.management.*:type=DiskUsage")});
            CATEGORIES.put("system", new ObjectName[]{new ObjectName("org.exist.management:type=SystemInfo")});
            CATEGORIES.put("caches", new ObjectName[]{
                        new ObjectName("org.exist.management.exist:type=CacheManager"),
                        new ObjectName("org.exist.management.exist:type=CollectionCacheManager"),
                        new ObjectName("org.exist.management.exist:type=CacheManager.Cache,*")
                    });
            CATEGORIES.put("locking", new ObjectName[]{new ObjectName("org.exist.management:type=LockManager")});
            CATEGORIES.put("processes", new ObjectName[]{new ObjectName("org.exist.management.*:type=ProcessReport")});
            CATEGORIES.put("sanity", new ObjectName[]{new ObjectName("org.exist.management.*.tasks:type=SanityReport")});
            CATEGORIES.put("all", new ObjectName[]{new ObjectName("org.exist.*:*"), onMemory, onRuntime});
        } catch (final MalformedObjectNameException e) {
            LOG.warn("Error in initialization: " + e.getMessage(), e);
        } catch (final NullPointerException e) {
            LOG.warn("Error in initialization: " + e.getMessage(), e);
        }
    }
	
	private final static Properties defaultProperties = new Properties();
	static {
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
	}
	
	public final static String JMX_NAMESPACE = "http://exist-db.org/jmx";
	public final static String JMX_PREFIX = "jmx";

	private static final QName ROW_ELEMENT = new QName("row", JMX_NAMESPACE, JMX_PREFIX);
	
	public final static QName JMX_ELEMENT = new QName("jmx", JMX_NAMESPACE, JMX_PREFIX);

	private static final QName JMX_CONNECTION_ATTR = new QName("connection");
	
	private static final QName JMX_ERROR = new QName("error", JMX_NAMESPACE, JMX_PREFIX);
	
	public static final long PING_TIMEOUT = -99;

	
	private final MBeanServerConnection platformConnection = ManagementFactory.getPlatformMBeanServer();
        private MBeanServerConnection connection;
    private JMXServiceURL url;
    
    private long ping = -1;
    
	public JMXtoXML() {
	}

	/**
	 * Connect to the local JMX instance.
	 */
	public void connect() {
		final ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
		if (servers.size() > 0) {
            connection = servers.get(0);
        }
	}
	
	/**
	 * Connect to a remote JMX instance using address and port.
	 * 
	 * @param address
	 * @param port
	 * @throws IOException
	 */
	public void connect(String address, int port) throws IOException {
        url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+address+":" + port + "/jmxrmi");
        final Map<String, String[]> env = new HashMap<String, String[]>();
        final String[] creds = {"guest", "guest"};
        env.put(JMXConnector.CREDENTIALS, creds);

        final JMXConnector jmxc = JMXConnectorFactory.connect(url, env);
        connection = jmxc.getMBeanServerConnection();

        LOG.debug("Connected to JMX server at " + url.toString());
    }
	
	/**
	 * Retrieve JMX output for the given categories and return a string of XML.
	 * Valid categories are "memory", "instances", "disk", "system", "caches",
	 * "locking", "processes", "sanity", "all".
	 * 
	 * @param categories
	 * @return report
	 * @throws TransformerException
	 */
	public String generateReport(String categories[]) throws TransformerException {
		final Element root = generateXMLReport(null, categories);
		final StringWriter writer = new StringWriter();
		final DOMSerializer streamer = new DOMSerializer(writer, defaultProperties);
		streamer.serialize(root);
		return writer.toString();
	}
	
	/**
	 * Ping the database to see if it is still responsive.
	 * This will first try to get a database broker object
	 * and if it succeeds, run a simple query. If the server does
	 * not respond within the given timeout, the method will return
	 * an error code -99 ({@link JMXtoXML#PING_TIMEOUT}). If there's an
	 * error on the server, the return value will be less than 0. Otherwise
	 * the return value is the response time in milliseconds.
	 * 
	 * @param instance the name of the database instance (default instance is "exist")
	 * @param timeout a timeout in milliseconds
	 * @return Response time in msec, less than 0 in case of an error on server
     * or PING_TIMEOUT when server does not respond in time
	 */
	public long ping(String instance, long timeout) {
		ping = SanityReport.PING_WAITING;
		final long start = System.currentTimeMillis();
		final Ping thread = new Ping(instance);
		thread.start();
		synchronized (this) {
			while (ping == SanityReport.PING_WAITING) {
				try {
					wait(100);
				} catch (final InterruptedException e) {
				}
				if ((System.currentTimeMillis() - start) >= timeout) {
					return PING_TIMEOUT;
				}
			}
			return ping;
		}
	}
	
	private class Ping extends Thread {
		
		private String instance;
		
		public Ping(String instance) {
			this.instance = instance;
		}
		
        @Override
		public void run() {
			try {
				final ObjectName name = new ObjectName("org.exist.management." + instance + ".tasks:type=SanityReport");
				ping = (Long) connection.invoke(name, "ping", new Object[] { Boolean.TRUE }, new String[] { boolean.class.getName() });
			} catch (final Exception e) {
				LOG.warn(e.getMessage(), e);
				ping = SanityReport.PING_ERROR;
			}
			synchronized (this) {
				notifyAll();
			}
		}
	}
	
	/**
	 * Retrieve JMX output for the given categories and return it as an XML DOM.
	 * Valid categories are "memory", "instances", "disk", "system", "caches",
	 * "locking", "processes", "sanity", "all".
	 * 
	 * @param errcode an optional error description
	 * @param categories
	 * @return xml report
	 * @throws TransformerException
	 */
	public Element generateXMLReport(String errcode, String categories[]) {
		final MemTreeBuilder builder = new MemTreeBuilder();
		
		try {
			builder.startDocument();
			
			builder.startElement(JMX_ELEMENT, null);
			if (url != null) {
                builder.addAttribute(JMX_CONNECTION_ATTR, url.toString());
            }
			
			if (errcode != null) {
				builder.startElement(JMX_ERROR, null);
				builder.characters(errcode);
				builder.endElement();
			}
			for (final String category : categories) {
				final ObjectName[] names = CATEGORIES.get(category);
				for (final ObjectName name : names) {
					queryMBeans(builder, name);
				}
			}
			
			builder.endElement();
			
			builder.endDocument();
		} catch (final Exception e) {
			e.printStackTrace();
			LOG.warn("Could not generate XML report from JMX: " + e.getMessage());
		}
		return (Element) builder.getDocument().getNode(1);
	}
	
	private void queryMBeans(MemTreeBuilder builder, ObjectName query)
		throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException,
			SAXException, AttributeNotFoundException, MBeanException, MalformedObjectNameException, NullPointerException {

                MBeanServerConnection conn = connection;
		Set<ObjectName> beans = conn.queryNames(query, null);

                //if the query is not found in the eXist specific MBeans server, then attempt to query the platform for it
                if(beans.isEmpty()) {
                    beans = platformConnection.queryNames(query, null);
                    conn = platformConnection;
                } //TODO examine JUnit source code as alternative method



		for (final ObjectName name : beans) {
			final MBeanInfo info = conn.getMBeanInfo(name);

			String className = info.getClassName();
			final int p = className.lastIndexOf('.');
			if (p > -1 && p + 1 < className.length()) {
                className = className.substring(p + 1);
            }

			final QName qname = new QName(className, JMX_NAMESPACE, JMX_PREFIX);
			builder.startElement(qname, null);
			builder.addAttribute(new QName("name"), name.toString());

			final MBeanAttributeInfo[] beanAttribs = info.getAttributes();
			for (int i = 0; i < beanAttribs.length; i++) {
				if (beanAttribs[i].isReadable()) {
					try {
						final QName attrQName = new QName(beanAttribs[i].getName(), JMX_NAMESPACE, JMX_PREFIX);
						final Object attrib = conn.getAttribute(name, beanAttribs[i].getName());

						builder.startElement(attrQName, null);
						serializeObject(builder, attrib);
						builder.endElement();
					} catch (final Exception e) {
						LOG.debug("exception caught: " + e.getMessage(), e);
					}
				}
			}
			builder.endElement();
		}
	}
	
	private void serializeObject(MemTreeBuilder builder, Object object) throws SAXException {
		if (object == null) {
            return;
        }
        
        if (object instanceof TabularData) {
            serialize(builder, (TabularData) object);
            
        } else if (object instanceof CompositeData) {
            serialize(builder, (CompositeData) object);
            
        } else if (object instanceof Object[]) {
            serialize(builder, (Object[]) object);
            
        } else {
            builder.characters(object.toString());
        }
	}
	
	private void serialize(MemTreeBuilder builder, Object[] data) throws SAXException {
		for (final Object o : data) {
			serializeObject(builder, o);
		}
	}

	private void serialize(MemTreeBuilder builder, CompositeData data) throws SAXException {
		final CompositeType type = data.getCompositeType();
		for (final String key : type.keySet()) {
			final QName qname = new QName(key, JMX_NAMESPACE, JMX_PREFIX);
			builder.startElement(qname, null);
			serializeObject(builder, data.get(key));
			builder.endElement();
		}
	}
	
	private void serialize(MemTreeBuilder builder, TabularData data) throws SAXException {
		final CompositeType rowType = data.getTabularType().getRowType();
		for (final Object rowObj : data.values()) {
			final CompositeData row = (CompositeData) rowObj;
			builder.startElement(ROW_ELEMENT, null);
			for (final String key : rowType.keySet()) {
				final Object columnData = row.get(key.toString());
				final QName columnQName = new QName(key.toString(), JMX_NAMESPACE, JMX_PREFIX);
				builder.startElement(columnQName, null);
				serializeObject(builder, columnData);
				builder.endElement();
			}
			builder.endElement();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final File home = ConfigurationHelper.getExistHome();
		DOMConfigurator.configure(new File(home, "log4j.xml").getAbsolutePath());
		
		final JMXtoXML client = new JMXtoXML();
		try {
			client.connect("localhost", 1099);
			System.out.println(client.generateReport(args));
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final TransformerException e) {
			e.printStackTrace();
		}
	}

}
