/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.security.utils;

import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.RealmImpl;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;
import org.exist.util.EXistInputSource;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ConverterFrom1_0 {
    
    public final static String LEGACY_USERS_DOCUMENT_PATH = "/db/system/users.xml";

	private final static String GROUP = "group";
	private final static String NAME = "name";
	private final static String PASS = "password";
	private final static String DIGEST_PASS = "digest-password";
	private final static String USER_ID = "uid";
	private final static String HOME = "home";

	public static void convert(EXistInputSource is) {
	}
	
	public static void convert(final DBBroker broker, final SecurityManager sm, final Document acl) throws PermissionDeniedException, EXistException {
		Element docElement = null;
		if (acl != null)
			{docElement = acl.getDocumentElement();}
		if (docElement == null) {
		} else {
			//Realm realm = sm.getRealm(RealmImpl.ID);
			
//			int nextGroupId = -1;
//			int nextUserId = -1;

			final Element root = acl.getDocumentElement();
			final Attr version = root.getAttributeNode("version");
			int major = 0;
			int minor = 0;
			if (version != null) {
				final String[] numbers = version.getValue().split("\\.");
				major = Integer.parseInt(numbers[0]);
				minor = Integer.parseInt(numbers[1]);
			}
			final NodeList nl = root.getChildNodes();
			Node node;
			Element next;
			Account account;
			NodeList ul;
//			String lastId;
			Group group;
			for (int i = 0; i < nl.getLength(); i++) {
				if (nl.item(i).getNodeType() != Node.ELEMENT_NODE)
					{continue;}
				next = (Element) nl.item(i);
				if ("users".equals(next.getTagName())) {
//					lastId = next.getAttribute("last-id");
//					try {
//						nextUserId = Integer.parseInt(lastId);
//					} catch (NumberFormatException e) {
//					}
					ul = next.getChildNodes();
					for (int j = 0; j < ul.getLength(); j++) {
						node = ul.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE && "user".equals(node.getLocalName())) {
							account = createAccount(major, minor, (Element) node);
							
							if (sm.hasAccount(account.getName())) {
                                sm.updateAccount(account);
							} else {
								sm.addAccount(account);
							}
						}
					}
				} else if ("groups".equals(next.getTagName())) {
//					lastId = next.getAttribute("last-id");
//					try {
//						nextGroupId = Integer.parseInt(lastId);
//					} catch (NumberFormatException e) {
//					}
					ul = next.getChildNodes();
					for (int j = 0; j < ul.getLength(); j++) {
						node = ul.item(j);
						if (node.getNodeType() == Node.ELEMENT_NODE && "group".equals(node.getLocalName())) {
							group = createGroup((Element) node);

							if (sm.hasGroup(group.getName())) {
								sm.updateGroup(group);
							} else {
								sm.addGroup(broker, group);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Read a account information from the given DOM node
	 * 
	 * @param node
	 *            Description of the Parameter
	 *@exception DatabaseConfigurationException
	 *                Description of the Exception
	 * @throws ConfigurationException 
	 * @throws PermissionDeniedException 
	 * @throws DOMException 
	 */
	public static Account createAccount(int majorVersion, int minorVersion, Element node)
			throws ConfigurationException, DOMException, PermissionDeniedException {
		
		String password = null;
		String digestPassword = null;
		
		int id = -1;
		XmldbURI home = null;
		
		final String name = node.getAttribute(NAME);
		if (name == null ) //|| name.length() == 0
			{throw new ConfigurationException("account needs a name");}

		if (majorVersion != 0) {
			final Attr attr = node.getAttributeNode(PASS);
			password = attr == null ? null : attr.getValue();
		}

		final Attr userId = node.getAttributeNode(USER_ID);
		if (userId == null)
			{throw new ConfigurationException("attribute id is missing");}
		try {
			id = Integer.parseInt(userId.getValue());
		} catch (final NumberFormatException e) {
			throw new ConfigurationException("illegal user id: " + userId + " for account " + name);
		}
		
		//TODO: workaround for 'null' admin's password. It should be removed after 6 months (@ 10 July 2010)
		if (id == 1 && password == null) {password = "";}
		
		final Account new_account = new UserAider(RealmImpl.ID, name);
		new_account.setPassword(password);
		
		final NodeList gl = node.getChildNodes();
		Node group;
		for (int i = 0; i < gl.getLength(); i++) {
			group = gl.item(i);
			if (group.getNodeType() == Node.ELEMENT_NODE && group.getLocalName().equals(GROUP))
				{new_account.addGroup(group.getFirstChild().getNodeValue());}
		}
		
		return new_account;
	}
	
	public static Group createGroup(Element element) {
		return new GroupAider(RealmImpl.ID, element.getAttribute("name")); //, Integer.parseInt(element.getAttribute("id")
	}

}
