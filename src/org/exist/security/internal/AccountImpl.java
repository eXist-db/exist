/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2010 The eXist Project
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
package org.exist.security.internal;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.security.MessageDigester;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.ldap.LDAPbindSecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashSet;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

/**
 * Represents a user within the database.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
@ConfigurationClass("account")
public class AccountImpl extends AbstractAccount {

	private final static Logger LOG = Logger.getLogger(AccountImpl.class);

	private final static String GROUP = "group";
	private final static String NAME = "name";
	private final static String PASS = "password";
	private final static String DIGEST_PASS = "digest-password";
	private final static String USER_ID = "uid";
	private final static String HOME = "home";

	public static int PASSWORD_ENCODING;
	public static boolean CHECK_PASSWORDS = true;

	static {
		Properties props = new Properties();
		try {
			props.load(AccountImpl.class.getClassLoader().getResourceAsStream(
					"org/exist/security/security.properties"));
		} catch (IOException e) {
		}
		String option = props.getProperty("passwords.encoding", "md5");
		setPasswordEncoding(option);
		option = props.getProperty("passwords.check", "yes");
		CHECK_PASSWORDS = option.equalsIgnoreCase("yes")
				|| option.equalsIgnoreCase("true");
	}

	static public void enablePasswordChecks(boolean check) {
		CHECK_PASSWORDS = check;
	}

	static public void setPasswordEncoding(String encoding) {
		if (encoding != null) {
			LOG.equals("Setting password encoding to " + encoding);
			if (encoding.equalsIgnoreCase("plain")) {
				PASSWORD_ENCODING = PLAIN_ENCODING;
			} else if (encoding.equalsIgnoreCase("md5")) {
				PASSWORD_ENCODING = MD5_ENCODING;
			} else {
				PASSWORD_ENCODING = SIMPLE_MD5_ENCODING;
			}
		}
	}

	static public Subject getUserFromServletRequest(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal instanceof Subject) {
			return (Subject) principal;
			
		//workaroud strange jetty authentication method, why encapsulate user object??? -shabanovd 
        } else if (principal != null && "org.eclipse.jetty.plus.jaas.JAASUserPrincipal".equals(principal.getClass().getName()) ) {
        	try {
        		Method method = principal.getClass().getMethod("getSubject");
        		Object obj = method.invoke(principal);
				if (obj instanceof javax.security.auth.Subject) {
					javax.security.auth.Subject subject = (javax.security.auth.Subject) obj;
					for (Principal _principal_ : subject.getPrincipals()) {
				        if (_principal_ instanceof Subject) {
							return (Subject) _principal_;
				        }
					}
				}
			} catch (SecurityException e) {
			} catch (IllegalArgumentException e) {
			} catch (IllegalAccessException e) {
			} catch (NoSuchMethodException e) {
			} catch (InvocationTargetException e) {
			}
		}
        
        return null;
	}
	
	@ConfigurationFieldAsElement("password")
	private String password = null;

	@ConfigurationFieldAsElement("digestPassword") 
	private String digestPassword = null;

	/**
	 * Create a new user with name and password
	 * 
	 * @param user
	 *            Description of the Parameter
	 * @param password
	 *            Description of the Parameter
	 * @throws ConfigurationException 
	 */
	public AccountImpl(AbstractRealm realm, int id, String name, String password) throws ConfigurationException {
		super(realm, id, name);
		setPassword(password);
	}

	/**
	 * Create a new user with name
	 * 
	 * @param name
	 *            The account name
	 * @throws ConfigurationException 
	 */
	public AccountImpl(AbstractRealm realm, String name) throws ConfigurationException {
		super(realm, -1, name);
	}

	/**
	 * Create a new user with name, password and primary group
	 * 
	 *@param user
	 *            Description of the Parameter
	 *@param password
	 *            Description of the Parameter
	 *@param primaryGroup
	 *            Description of the Parameter
	 * @throws ConfigurationException 
	 */
	public AccountImpl(AbstractRealm realm, int id, String name, String password, String primaryGroup) throws ConfigurationException {
		this(realm, id, name, password);
		defaultRole = addGroup(primaryGroup);
	}

	/**
	 * Read a new user from the given DOM node
	 * 
	 *@param node
	 *            Description of the Parameter
	 *@exception DatabaseConfigurationException
	 *                Description of the Exception
	 * @throws ConfigurationException 
	 */
	public static AccountImpl createAccount(AbstractRealm realm, int majorVersion, int minorVersion, Element node)
			throws DatabaseConfigurationException, ConfigurationException {
		
		String password = null;
		String digestPassword = null;
		
		int id = -1;
		XmldbURI home = null;
		
		String name = node.getAttribute(NAME);
		if (name == null ) //|| name.length() == 0
			throw new DatabaseConfigurationException("user needs a name");
		
		Attr attr;
		if (majorVersion == 0) {
			attr = node.getAttributeNode(PASS);
			digestPassword = attr == null ? null : attr.getValue();
		} else {
			attr = node.getAttributeNode(PASS);
			password = attr == null ? null : attr.getValue();
//			if (password.charAt(0) == '{') {
//				throw new DatabaseConfigurationException(
//						"Unrecognized password encoding " + password + " for user " + name);
//			}

			attr = node.getAttributeNode(DIGEST_PASS);
			digestPassword = attr == null ? null : attr.getValue();
		}
		Attr userId = node.getAttributeNode(USER_ID);
		if (userId == null)
			throw new DatabaseConfigurationException("attribute id missing");
		try {
			id = Integer.parseInt(userId.getValue());
		} catch (NumberFormatException e) {
			throw new DatabaseConfigurationException("illegal user id: "
					+ userId + " for user " + name);
		}
		Attr homeAttr = node.getAttributeNode(HOME);
		home = homeAttr == null ? null : XmldbURI.create(homeAttr.getValue());
		
		//TODO: workaround for 'null' admin's password. It should be removed after 6 months (@ 10 July 2010)
		if (id == 1 && password == null) password = "";
		
		AccountImpl new_account = new AccountImpl(realm, id, name, password);
		new_account.setHome(home);
		
		NodeList gl = node.getChildNodes();
		Node group;
		for (int i = 0; i < gl.getLength(); i++) {
			group = gl.item(i);
			if (group.getNodeType() == Node.ELEMENT_NODE && group.getLocalName().equals(GROUP))
				new_account.addGroup(group.getFirstChild().getNodeValue());
		}
		
		return new_account;
	}
	
    public AccountImpl(AbstractRealm realm, int id, Account from_user) throws ConfigurationException {
        super(realm, id, from_user.getName());

        home = from_user.getHome();
        
        defaultRole = from_user.getDefaultGroup();

        if (from_user instanceof AccountImpl) {
			AccountImpl user = (AccountImpl) from_user;

	        defaultRole = user.defaultRole;
	        roles = user.roles;

	        password = user.password;
	        digestPassword = user.digestPassword;
	        
	        hasDbaRole = user.hasDbaRole;

	        _cred = user._cred;
		} else if (from_user instanceof UserAider) {
			UserAider user = (UserAider) from_user;
			
			String[] gl = user.getGroups();
			for (int i = 0; i < gl.length; i++) {
				addGroup(gl[i]);
			}

	        setPassword(user.getPassword());
	        digestPassword = user.getDigestPassword();
		}

    }

	public AccountImpl(AbstractRealm realm, AccountImpl from_user) throws ConfigurationException {
		super(realm, from_user.id, from_user.name);

		home = from_user.home;
        
        defaultRole = from_user.defaultRole;
        roles = from_user.roles;

        password = from_user.password;
        digestPassword = from_user.digestPassword;
        
        hasDbaRole = from_user.hasDbaRole;

        _cred = from_user._cred;

        this.realm = realm;
    }

	/**
	 * Get the user's password
	 * 
	 * @return Description of the Return Value
	 * @deprecated
	 */
	public final String getPassword() {
		return password;
	}

	@Deprecated
	public final String getDigestPassword() {
		return digestPassword;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.security.User#setPassword(java.lang.String)
	 */
	public final void setPassword(String passwd) {
    	_cred = new Password(passwd);

		if (passwd == null) {
			this.password = null;
			this.digestPassword = null;
		} else {
			this.password = _cred.toString();
			this.digestPassword = digest(passwd);
		}
	}

	/**
	 * Sets the digest passwod value of the User object
	 * 
	 * @param passwd
	 *            The new passwordDigest value
	 * @deprecated
	 */
	public final void setPasswordDigest(String passwd) {
		//setPassword(passwd);
		this.digestPassword = (passwd == null) ? null : passwd;
	}

	/**
	 * Sets the encoded passwod value of the User object
	 * 
	 * @param passwd
	 *            The new passwordDigest value
	 * @deprecated
	 */
	public final void setEncodedPassword(String passwd) {
		setPassword("{MD5}"+passwd);
		this.password = (passwd == null) ? null : passwd;
	}

	public final String digest(String passwd) {
		switch (PASSWORD_ENCODING) {
		case PLAIN_ENCODING:
			return passwd;
		case MD5_ENCODING:
			return MessageDigester
					.md5(name + ":" + realm.getId() + ":" + passwd, false);
		default:
			return MessageDigester.md5(passwd, true);
		}

	}

	/**
	 * Split up the validate method into two, to make it possible to
	 * authenticate users, which are not defined in the instance named "exist"
	 * without having impact on the standard functionality.
	 * 
	 * @param passwd
	 * @return true if the password was correct, false if not, or if there was a
	 *         problem.
	 */
	@Deprecated //use SecurityManager.authenticate
	public final boolean validate(String passwd) {
		SecurityManager sm;
		try {
			sm = BrokerPool.getInstance().getSecurityManager();
			return validate(passwd, sm);
		} catch (EXistException e) {
			LOG.warn("Failed to get security manager in validate: ", e);
			return false;
		}
	}

	@Deprecated //use SecurityManager.authenticate
	public final boolean validate(String passwd, SecurityManager sm) {
		// security management is disabled if in repair mode
		if (!CHECK_PASSWORDS)
			return true;

		if (password == null && digestPassword == null) {
			return true;
		} else if (id == 1 && passwd == null) {
			passwd = "";
		}
		if (passwd == null) {
			return false;
		}

		// [ 1557095 ] LDAP passwords patch
		// Try to authenticate using LDAP
		if (sm != null) {
			if (sm instanceof LDAPbindSecurityManager) {
				if (((LDAPbindSecurityManager) sm).bind(name, passwd))
					return true;
				else
					return false;
			}
		}

		if (password != null) {
			if (MessageDigester.md5(passwd, true).equals(password)) {
				return true;
			}
		}
		if (digestPassword != null) {
			if (digest(passwd).equals(digestPassword)) {
				return true;
			}
		}
		return false;
	}

	@Deprecated //use SecurityManager.authenticate
	public final boolean validateDigest(String passwd) {
		if (digestPassword == null)
			return true;
		if (passwd == null)
			return false;
		return digest(passwd).equals(digestPassword);
	}
}
