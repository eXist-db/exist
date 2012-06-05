/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
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
 *  $Id$
 */
package org.exist.atom.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.AtomModule;
import org.exist.atom.modules.AtomFeeds;
import org.exist.atom.modules.AtomProtocol;
import org.exist.atom.modules.Query;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.http.servlets.AbstractExistHttpServlet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.validation.XmlLibraryChecker;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Implements a rest interface for exist collections as atom feeds
 * 
 * @author Alex Milowski
 */
public class AtomServlet extends AbstractExistHttpServlet {

	private static final long serialVersionUID = 1L;

	public final static String DEFAULT_ENCODING = "UTF-8";
	public final static String CONF_NS = "http://www.exist-db.org/Vocabulary/AtomConfiguration/2006/1/0";

	protected final static Logger LOG = Logger.getLogger(AtomServlet.class);

	@Override
	public Logger getLog() {
		return LOG;
	}

	/**
	 * Module contexts that default to using the servlet's config
	 */
	class ModuleContext implements AtomModule.Context {
		ServletConfig config;
		String moduleLoadPath;

		ModuleContext(ServletConfig config, String subpath, String moduleLoadPath) {
			this.config = config;
			this.moduleLoadPath = moduleLoadPath;
		}

		@Override
		public String getDefaultCharset() {
			return formEncoding;
		}

		@Override
		public String getParameter(String name) {
			return config.getInitParameter(name);
		}

		@Override
		public String getContextPath() {
			// TODO: finish
			return null;
		}

		@Override
		public URL getContextURL() {
			// TODO: finish
			return null;
		}

		@Override
		public String getModuleLoadPath() {
			return moduleLoadPath;
		}
	}

	private Map<String, AtomModule> modules;
	private Map<String, Boolean> noAuth;

	private String formEncoding = null;
	private BrokerPool pool = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// Load all the modules
		// modules = new HashMap<String,AtomModule>();
		modules = new HashMap<String, AtomModule>();
		noAuth = new HashMap<String, Boolean>();

		String configFileOpt = config.getInitParameter("config-file");
		File dbHome = pool.getConfiguration().getExistHome();

		File atomConf;
		if (configFileOpt == null)
			atomConf = new File(dbHome, "atom-services.xml");
		else
			atomConf = new File(config.getServletContext().getRealPath(
					configFileOpt));

		config.getServletContext().log(
				"Checking for atom configuration in "
						+ atomConf.getAbsolutePath());

		if (atomConf.exists()) {
			config.getServletContext().log("Loading configuration " + atomConf.getAbsolutePath());
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = null;
			Document confDoc = null;
			InputStream is = null;
			try {
				is = new FileInputStream(atomConf);
				InputSource src = new InputSource(new InputStreamReader(is, formEncoding));
				URI docBaseURI = atomConf.toURI();
				src.setSystemId(docBaseURI.toString());
				docBuilder = docFactory.newDocumentBuilder();

				confDoc = docBuilder.parse(src);
				confDoc.getDocumentElement();

				// Add all the modules
				NodeList moduleConfList = confDoc.getElementsByTagNameNS(CONF_NS, "module");
				for (int i = 0; i < moduleConfList.getLength(); i++) {
					Element moduleConf = (Element) moduleConfList.item(i);
					String name = moduleConf.getAttribute("name");
					if (modules.get(name) != null) {
						throw new ServletException("Module '" + name
								+ "' is configured more than once ( child # "
								+ (i + 1));
					}

					if ("false".equals(moduleConf.getAttribute("authenticate"))) {
						noAuth.put(name, Boolean.TRUE);
					}

					String className = moduleConf.getAttribute("class");
					if (className != null && className.length() > 0) {
						try {
							Class<?> moduleClass = Class.forName(className);
							AtomModule amodule = (AtomModule) moduleClass.newInstance();
							modules.put(name, amodule);
							amodule.init(new ModuleContext(config, name, atomConf.getParent()));

						} catch (Exception ex) {
							throw new ServletException(
									"Cannot instantiate class " + className
											+ " for module '" + name
											+ "' due to exception: "
											+ ex.getMessage(), ex);
						}

					} else {
						// no class means query
						Query query = new Query();
						modules.put(name, query);

						String allowQueryPost = moduleConf.getAttribute("query-by-post");
						if ("true".equals(allowQueryPost)) {
							query.setQueryByPost(true);
						}

						NodeList methodList = moduleConf.getElementsByTagNameNS(CONF_NS, "method");

						for (int m = 0; m < methodList.getLength(); m++) {
							Element methodConf = (Element) methodList.item(m);
							String type = methodConf.getAttribute("type");
							if (type == null) {
								getLog().warn(
										"No type specified for method in module "
												+ name);
								continue;
							}

							// What I want but can't have because of JDK 1.4
							// URI baseURI =
							// URI.create(methodConf.getBaseURI());
							URI baseURI = docBaseURI;
							String queryRef = methodConf.getAttribute("query");
							if (queryRef == null) {
								getLog().warn(
										"No query specified for method " + type
												+ " in module " + name);
								continue;
							}

							boolean fromClasspath = "true".equals(methodConf.getAttribute("from-classpath"));
							Query.MethodConfiguration mconf = query
									.getMethodConfiguration(type);
							if (mconf == null) {
								getLog().warn(
										"Unknown method " + type
												+ " in module " + name);
								continue;
							}

							String responseContentType = methodConf.getAttribute("content-type");
							if (responseContentType != null
									&& responseContentType.trim().length() != 0) {
								mconf.setContentType(responseContentType);
							}

							URL queryURI = null;
							if (fromClasspath) {
								getLog().debug(
										"Nope. Attempting to get resource "
												+ queryRef + " from "
												+ Atom.class.getName());
								queryURI = Atom.class.getResource(queryRef);

							} else {
								queryURI = baseURI.resolve(queryRef).toURL();
							}

							getLog().debug(
									"Loading from module " + name + " method "
											+ type + " from resource "
											+ queryURI + " via classpath("
											+ fromClasspath + ") and ref ("
											+ queryRef + ")");

							if (queryURI == null) {
								throw new ServletException(
										"Cannot find resource " + queryRef
												+ " for module " + name);
							}
							mconf.setQuerySource(queryURI);
						}
						query.init(new ModuleContext(config, name, atomConf
								.getParent()));

					}
				}

			} catch (IOException e) {
				getLog().warn(e);
				throw new ServletException(e.getMessage());
			} catch (SAXException e) {
				getLog().warn(e);
				throw new ServletException(e.getMessage());
			} catch (ParserConfigurationException e) {
				getLog().warn(e);
				throw new ServletException(e.getMessage());
			} catch (EXistException e) {
				getLog().warn(e);
				throw new ServletException(e.getMessage());
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException ex) {
					}
				}
			}

		} else {
			try {
				AtomProtocol protocol = new AtomProtocol();
				modules.put("edit", protocol);
				protocol.init(new ModuleContext(config, "edit", dbHome.getAbsolutePath()));

				AtomFeeds feeds = new AtomFeeds();
				modules.put("content", feeds);
				feeds.init(new ModuleContext(config, "content", dbHome.getAbsolutePath()));

				Query query = new Query();
				query.setQueryByPost(true);
				modules.put("query", query);
				query.init(new ModuleContext(config, "query", dbHome.getAbsolutePath()));

				Query topics = new Query();
				modules.put("topic", topics);
				topics.getMethodConfiguration("GET").setQuerySource(
						topics.getClass().getResource("topic.xq"));
				topics.init(new ModuleContext(config, "topic", dbHome.getAbsolutePath()));

				Query introspect = new Query();
				modules.put("introspect", introspect);
				introspect.getMethodConfiguration("GET").setQuerySource(
						introspect.getClass().getResource("introspect.xq"));
				introspect.init(new ModuleContext(config, "introspect", dbHome.getAbsolutePath()));

			} catch (EXistException ex) {
				throw new ServletException("Exception during module init(): "
						+ ex.getMessage(), ex);
			}
		}

		// XML lib checks....
		XmlLibraryChecker.check();
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException {

		try {
			// Get the path
			String path = request.getPathInfo();

			if (path == null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"URL has no extra path information specified.");
				return;
			}

			int firstSlash = path.indexOf('/', 1);
			if (firstSlash < 0 && path.length() == 1) {
				response.sendError(400, "Module not specified.");
				return;
			}

			String moduleName = firstSlash < 0 ? path.substring(1) : path
					.substring(1, firstSlash);
			path = firstSlash < 0 ? "" : path.substring(firstSlash);

			AtomModule module = modules.get(moduleName);
			if (module == null) {
				response.sendError(400, "Module " + moduleName + " not found.");
				return;
			}

			Subject user = null;
			if (noAuth.get(moduleName) == null) {
				// Authenticate
				user = authenticate(request, response);
				if (user == null) {
					// You now get a challenge if there is no user
					return;
				}
			}

			// Handle the resource
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				module.process(broker, new HttpRequestMessage(request, path,
						'/' + moduleName), new HttpResponseMessage(response));

			} catch (NotFoundException ex) {
				getLog().info(
						"Resource " + path + " not found by " + moduleName, ex);
				response.sendError(HttpServletResponse.SC_NOT_FOUND,
						ex.getMessage());

			} catch (PermissionDeniedException ex) {
				getLog().info(
						"Permission denied to " + path + " by " + moduleName
								+ " for " + user.getName(), ex);
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
						ex.getMessage());

			} catch (BadRequestException ex) {
				getLog().info("Bad request throw from module " + moduleName, ex);
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						ex.getMessage());

			} catch (EXistException ex) {
				getLog().fatal(
						"Exception getting broker from pool for user "
								+ user.getName(), ex);
				response.sendError(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Service is not available.");

			} catch (Throwable e) {
				getLog().error(e.getMessage(), e);
				throw new ServletException("An error occurred: "
						+ e.getMessage(), e);

			} finally {
				pool.release(broker);
			}

		} catch (IOException ex) {
			getLog().fatal("I/O exception on request.", ex);
			try {
				response.sendError(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Service is not available.");
			} catch (IOException finalEx) {
				getLog().fatal("Cannot return 500 on exception.", ex);
			}
		}
	}
}