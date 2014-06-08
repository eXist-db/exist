/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.storage.md;

import java.util.Map;

import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ExtractConfig extends ExtractConfigAbstract {

	private static final String MATCH_ATTR = "match";
	private static final String KEY_ATTR = "key";

	private boolean isQNameIndex = false;

	private NodePath path = null;
	private String key = null;

	public ExtractConfig(Element elem, Map<String, String> namespaces) throws DatabaseConfigurationException {

		parse(elem, namespaces);
	}

	public String getKey() {
		return key;
	}

	private void parse(Element elem, Map<String, String> namespaces) throws DatabaseConfigurationException {
		if (elem.hasAttribute(QNAME_ATTR)) {
			QName qname = parseQName(elem, namespaces);
			path = new NodePath(qname);
			isQNameIndex = true;
		} else {
			String matchPath = elem.getAttribute(MATCH_ATTR);
			try {
				path = new NodePath(namespaces, matchPath);
				if (path.length() == 0)
					throw new DatabaseConfigurationException(
							"Metadata extractor: Invalid match path in collection config: "
									+ matchPath);
			} catch (IllegalArgumentException e) {
				throw new DatabaseConfigurationException(
						"Metadata extractor: invalid match path in configuration: "
								+ e.getMessage(), e);
			}
		}

		key = elem.getAttribute(KEY_ATTR);

		if (key == null || key.isEmpty()) {
			throw new DatabaseConfigurationException(
					"Metadata extractor: Invalid key in collection config: "
							+ key);
		}

		parseConfig(elem, namespaces);
	}
	public NodePath getNodePath() {
		return path;
	}

	public boolean match(NodePath other) {
		if (isQNameIndex)
			return other.getLastComponent().equalsSimple(path.getLastComponent());
		
		return path.match(other);
	}

	ExtractConfig nextConfig = null;
	
	public void add(ExtractConfig config) {
		if (nextConfig == null)
			nextConfig = config;
		else
			nextConfig.add(config);
	}
	
	public ExtractConfig getNext() {
		return nextConfig;
	}
}
