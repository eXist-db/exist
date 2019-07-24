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
package org.exist.security.internal;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.storage.BrokerPool;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class EXistDBLoginModule implements javax.security.auth.spi.LoginModule {

//	private final static Logger LOG = LogManager.getLogger(EXistLoginModule.class);

	// initial state
	private Subject subject;
	private CallbackHandler callbackHandler;
//	private Map<String, ?> sharedState;
//	private Map<String, ?> options;

	// configurable option
	private boolean debug = false;

	// the authentication status
	private boolean succeeded = false;
	private boolean commitSucceeded = false;
	private org.exist.security.Subject userPrincipal = null;

	/**
	 * Initialize this <code>LoginModule</code>.
	 * 
	 *
	 * 
	 * @param subject
	 *            the <code>Subject</code> to be authenticated.
	 *
	 * 
	 * @param callbackHandler
	 *            a <code>CallbackHandler</code> for communicating with the end
	 *            user (prompting for user names and passwords, for example).
	 *
	 * 
	 * @param sharedState
	 *            shared <code>LoginModule</code> state.
	 *
	 * 
	 * @param options
	 *            options specified in the login <code>Configuration</code> for
	 *            this particular <code>LoginModule</code>.
	 */
	public void initialize(Subject subject, CallbackHandler callbackHandler,
			Map<String, ?> sharedState, Map<String, ?> options) {

		this.subject = subject;
		this.callbackHandler = callbackHandler;
//		this.sharedState = sharedState;
//		this.options = options;

		// initialize any configured options
		debug = "true".equalsIgnoreCase((String) options.get("debug"));
	}

	/**
	 * Authenticate the user by prompting for a user name and password.
	 * 
	 *
	 * 
	 * @return true in all cases since this <code>LoginModule</code> should not
	 *         be ignored.
	 * 
	 * @throws FailedLoginException
	 *                if the authentication fails.
	 *
	 * 
	 * @throws LoginException
	 *                if this <code>LoginModule</code> is unable to perform the
	 *                authentication.
	 */
	public boolean login() throws LoginException {

		// prompt for a user name and password
		if (callbackHandler == null)
			{throw new LoginException("Error: no CallbackHandler available "
					+ "to garner authentication information from the user");}

		final Callback[] callbacks = new Callback[2];
		callbacks[0] = new NameCallback("user name: ");
		callbacks[1] = new PasswordCallback("password: ", false);

		// username and password
		String username;
		char[] password;

		try {
			callbackHandler.handle(callbacks);
			username = ((NameCallback) callbacks[0]).getName();
			char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();
			if (tmpPassword == null) {
				// treat a NULL password as an empty password
				tmpPassword = new char[0];
			}
			password = new char[tmpPassword.length];
			System.arraycopy(tmpPassword, 0, password, 0, tmpPassword.length);
			((PasswordCallback) callbacks[1]).clearPassword();

		} catch (final java.io.IOException ioe) {
			throw new LoginException(ioe.toString());
		} catch (final UnsupportedCallbackException uce) {
			throw new LoginException("Error: " + uce.getCallback().toString()
					+ " not available to garner authentication information"
					+ " from the user");
		}

		// print debugging information
		if (debug) {
			System.out.println("\t\t[eXistLoginModule] user entered user name: " + username);
		}
		
		try {
			userPrincipal = BrokerPool.getInstance().getSecurityManager().authenticate(username, password);
		} catch (final AuthenticationException e) {
			if (debug)
				{System.out.println("\t\t[eXistLoginModule] authentication failed");}
			throw new FailedLoginException(e.getMessage());
		} catch (final EXistException e) {
			throw new FailedLoginException(e.getMessage());
		}
		
		succeeded = userPrincipal.isAuthenticated();
		return true;
	}

	/**
	 *
	 * This method is called if the LoginContext's overall authentication
	 * succeeded (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
	 * LoginModules succeeded).
	 * 
	 *
	 * If this LoginModule's own authentication attempt succeeded (checked by
	 * retrieving the private state saved by the <code>login</code> method),
	 * then this method associates a <code>SamplePrincipal</code> with the
	 * <code>Subject</code> located in the <code>LoginModule</code>. If this
	 * LoginModule's own authentication attempted failed, then this method
	 * removes any state that was originally saved.
	 * 
	 *
	 * 
	 * @throws LoginException
	 *                if the commit fails.
	 * 
	 * @return true if this LoginModule's own login and commit attempts
	 *         succeeded, or false otherwise.
	 */
	public boolean commit() throws LoginException {
		if (!succeeded) {
			return false;
		} else {
			// add a Principal (authenticated identity)
			// to the Subject

			if (!subject.getPrincipals().contains(userPrincipal))
				{subject.getPrincipals().add(userPrincipal);}

			if (debug) {
				System.out.println("\t\t[eXistLoginModule] added User to Subject");
			}

			commitSucceeded = true;
			return true;
		}
	}

	/**
	 *
	 * This method is called if the LoginContext's overall authentication
	 * failed. (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
	 * LoginModules did not succeed).
	 * 
	 *
	 * If this LoginModule's own authentication attempt succeeded (checked by
	 * retrieving the private state saved by the <code>login</code> and
	 * <code>commit</code> methods), then this method cleans up any state that
	 * was originally saved.
	 * 
	 *
	 * 
	 * @throws LoginException
	 *                if the abort fails.
	 * 
	 * @return false if this LoginModule's own login and/or commit attempts
	 *         failed, and true otherwise.
	 */
	public boolean abort() throws LoginException {
		if (succeeded == false) {
			return false;
		} else if (succeeded && !commitSucceeded) {
			// login succeeded but overall authentication failed
			succeeded = false;
			userPrincipal = null;
		} else {
			// overall authentication succeeded and commit succeeded,
			// but someone else's commit failed
			logout();
		}
		return true;
	}

	/**
	 * Logout the user.
	 * 
	 *
	 * This method removes the <code>SamplePrincipal</code> that was added by
	 * the <code>commit</code> method.
	 * 
	 *
	 * 
	 * @throws LoginException
	 *                if the logout fails.
	 * 
	 * @return true in all cases since this <code>LoginModule</code> should not
	 *         be ignored.
	 */
	public boolean logout() throws LoginException {

		subject.getPrincipals().remove(userPrincipal);
		succeeded = commitSucceeded;
		userPrincipal = null;
		return true;
	}
}