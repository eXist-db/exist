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
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public abstract class ExtractConfigAbstract {

	protected final static String IGNORE_ELEMENT = "ignore";
	protected final static String INLINE_ELEMENT = "inline";

	protected final static String N_INLINE = "inline";
	protected final static String N_IGNORE = "ignore";

	protected static final String QNAME_ATTR = "qname";

	private Map<QName, String> specialNodes = null;

	protected void parseConfig(Element root, Map<String, String> namespaces)
			throws DatabaseConfigurationException {
		Node child = root.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (IGNORE_ELEMENT.equals(child.getLocalName())) {
					String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
					
					if (qnameAttr == null || qnameAttr.length() == 0)
						throw new DatabaseConfigurationException(
								"Metadata extractor configuration element 'ignore' needs an attribute 'qname'");
					
					if (specialNodes == null)
						specialNodes = new TreeMap<QName, String>();
					
					specialNodes.put(parseQName(qnameAttr, namespaces), N_IGNORE);
				
				} else if (INLINE_ELEMENT.equals(child.getLocalName())) {
					
					String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
					
					if (qnameAttr == null || qnameAttr.length() == 0)
						throw new DatabaseConfigurationException(
								"Metadata extractor configuration element 'inline' needs an attribute 'qname'");
					
					if (specialNodes == null)
						specialNodes = new TreeMap<QName, String>();
					
					specialNodes.put(parseQName(qnameAttr, namespaces), N_INLINE);
				}
			}
			child = child.getNextSibling();
		}
	}

	public QName parseQName(Element config, Map<String, String> namespaces) throws DatabaseConfigurationException {
		
		String name = config.getAttribute(QNAME_ATTR);
		
		if (name == null || name.length() == 0)
			throw new DatabaseConfigurationException(
					"Metadata extractor configuration error: element "
							+ config.getNodeName() + " must have an attribute "
							+ QNAME_ATTR);

		return parseQName(name, namespaces);
	}

	protected static QName parseQName(String name,
			Map<String, String> namespaces)
			throws DatabaseConfigurationException {
		boolean isAttribute = false;
		if (name.startsWith("@")) {
			isAttribute = true;
			name = name.substring(1);
		}
		try {
			String prefix = QName.extractPrefix(name);
			String localName = QName.extractLocalName(name);
			
			String namespaceURI = "";
			
			if (prefix != null) {
				namespaceURI = namespaces.get(prefix);
				if (namespaceURI == null) {
					throw new DatabaseConfigurationException(
							"Metadata extractor: No namespace defined for prefix: " + prefix
									+ " in index definition");
				}
			}
			
			QName qname = new QName(localName, namespaceURI, prefix);
			
			if (isAttribute)
				qname.setNameType(ElementValue.ATTRIBUTE);
			
			return qname;
			
		} catch (IllegalArgumentException e) {
			throw new DatabaseConfigurationException(
					"Metadata extractor configuration error: " + e.getMessage(), e);
		}
	}
	
	public boolean isIgnoredNode(QName qname) {
		return specialNodes != null && specialNodes.get(qname) == N_IGNORE;
	}

	public boolean isInlineNode(QName qname) {
		return specialNodes != null && specialNodes.get(qname) == N_INLINE;
	}
}
