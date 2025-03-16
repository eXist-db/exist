/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.servlets;

import org.exist.security.MessageDigester;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.AccountImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.storage.BrokerPool;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * An Authenticator that uses MD5 Digest Authentication.
 * 
 * @author wolf
 */
public class DigestAuthenticator implements Authenticator {

	private BrokerPool pool;

	public DigestAuthenticator(BrokerPool pool) {
		this.pool = pool;
	}

	@Override
	public Subject authenticate(
			HttpServletRequest request,
			HttpServletResponse response, 
			boolean sendChallenge) throws IOException {
		
		final String credentials = request.getHeader("Authorization");
		if (credentials == null) {
			sendChallenge(request, response);
			return null;
		}
		final Digest digest = new Digest(request.getMethod());
		parseCredentials(digest, credentials);
		final SecurityManager secman = pool.getSecurityManager();
		final AccountImpl user = (AccountImpl)secman.getAccount(digest.username);
		if (user == null) {
			// If user does not exist then send a challenge request again
			if (sendChallenge) {sendChallenge(request, response);}
			return null;
		}
		if (!digest.check(user.getDigestPassword())) {
			// If password is incorrect then send a challenge request again
			if (sendChallenge) {sendChallenge(request, response);}
			return null;
		}
		return new SubjectAccreditedImpl(user, this);
	}

    @Override
	public void sendChallenge(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		response.setHeader("WWW-Authenticate", "Digest realm=\"exist\", "
				+ "nonce=\"" + createNonce(request) + "\", " + "domain=\""
				+ request.getContextPath() + "\", " + "opaque=\""
				+ MessageDigester.md5(Integer.toString(hashCode(), 27), false)
				+ '"');
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

	private String createNonce(HttpServletRequest request) {
		return MessageDigester.md5(request.getRemoteAddr() + ':'
				+ System.currentTimeMillis() + ':'
				+ hashCode(), false);
	}

	private static void parseCredentials(Digest digest, String credentials) {
		credentials = credentials.substring("Digest ".length());
		final StringBuilder current = new StringBuilder();
		String name = null;
		String value;
		boolean inQuotedString = false;
		for (int i = 0; i < credentials.length(); i++) {
			final char ch = credentials.charAt(i);
			switch (ch) {
			case ' ':
				break;
			case '"':
			case '\'':
				if (inQuotedString) {
					value = current.toString();
					current.setLength(0);
					inQuotedString = false;
					if ("username".equalsIgnoreCase(name))
						{digest.username = value;}
					else if ("realm".equalsIgnoreCase(name))
						{digest.realm = value;}
					else if ("nonce".equalsIgnoreCase(name))
						{digest.nonce = value;}
					else if ("uri".equalsIgnoreCase(name))
						{digest.uri = value;}
					else if ("response".equalsIgnoreCase(name))
						{digest.response = value;}
				} else {
					value = null;
					inQuotedString = true;
				}
				break;
			case ',':
				name = null;
				break;
			case '=':
				name = current.toString();
				current.setLength(0);
				break;
			default:
				current.append(ch);
				break;
			}
		}
	}

	private static class Digest {
		String method = null;
		String username = null;
		@SuppressWarnings("unused")
		String realm = null;
		String nonce = null;
		String uri = null;
		String response = null;

		public Digest(String method) {
			this.method = method;
		}

		public boolean check(String credentials) throws IOException {
			if (credentials == null)
				// no password set for the user: return true
				{return true;}
			try {
				final MessageDigest md = MessageDigest.getInstance("MD5");

				// calc A2 digest
				md.reset();
				md.update(method.getBytes(ISO_8859_1));
				md.update((byte) ':');
				md.update(uri.getBytes(ISO_8859_1));
				final byte[] ha2 = md.digest();

				// calc digest
				md.update(credentials.getBytes(ISO_8859_1));
				md.update((byte) ':');
				md.update(nonce.getBytes(ISO_8859_1));
				md.update((byte) ':');
				md.update(MessageDigester.byteArrayToHex(ha2).getBytes(ISO_8859_1));
				final byte[] digest = md.digest();

				// check digest
				return (MessageDigester.byteArrayToHex(digest).equalsIgnoreCase(response));
			} catch (final NoSuchAlgorithmException e) {
				throw new RuntimeException("MD5 not supported");
			}

		}
	}
}
