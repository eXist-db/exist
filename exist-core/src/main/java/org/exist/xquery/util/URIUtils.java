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
package org.exist.xquery.util;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;

import org.exist.util.CodePointString;
import org.exist.xmldb.XmldbURI;

/**
 * Utilities for URI related functions
 * 
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */

public class URIUtils {

	private final static class CharArray {
		char[] buf;
		int count;

		public CharArray(final int initalSize) {
			buf = new char[initalSize];
		}

		void append(final char c) {
			final int newcount = count + 1;
			if (newcount > buf.length) {
				buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
			}
			buf[count] = c;
			count = newcount;
		}

		public void append(final char c, final char c1) {
			final int newcount = count + 2;
			if (newcount > buf.length) {
				buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
			}
			buf[count] = c;
			buf[count+1] = c1;
			count = newcount;
		}

		public void append(final char c, final char c1, final char c2) {
			final int newcount = count + 3;
			if (newcount > buf.length) {
				buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
			}
			buf[count] = c;
			buf[count+1] = c1;
			buf[count+2] = c2;
			count = newcount;
		}
	}

	private static final char[][] table = new char[][] {
		new char[] { 'a' }
	};

	private static final char[] HEX_TABLE = { '0','1','2','3','4','5','6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * Encodes reserved characters in a string that is intended to be used in the path segment of a URI.
	 *
	 * This function applies the URI escaping rules defined in <a href="https://www.ietf.org/rfc/rfc3986.html#section-2">RFC 3986 Section 2</a>.
	 * The effect of the function is to escape reserved characters.
	 * Each such character in the string is replaced with its percent-encoded form as described in RFC 3986.
	 *
	 * Since RFC 3986 recommends that, for consistency, URI producers and normalizers should use uppercase
	 * hexadecimal digits for all percent-encodings, this function must always generate hexadecimal values
	 * using the upper-case letters A-F.
	 *
	 * @param pathComponent the path component to URI encode.
	 *
	 * @return the URI encoded path component.
	 */
	public static String encodeForURI(final String pathComponent) {

		final CodePointString codePoints = new CodePointString(pathComponent);
		final CharArray buf = new CharArray(pathComponent.length());

		for (int i = 0; i < codePoints.length(); i++) {
			final int c = codePoints.codePointAt(i);
			if (c== '-' || c == '.'
					|| (c >= '0' && c <= '9')
					|| (c >= 'A' && c <= 'Z')
					|| c == '_'
					|| (c >= 'a' && c <= 'z')
					|| c == '~') {
				buf.append((char)c);

			} else {
				buf.append('%');

				if (c <= 0x7F) {
					buf.append(HEX_TABLE[c >> 4], HEX_TABLE[c & 0xF]);

				} else if (c <= 0x7FF) {
					final int c1 = 0xC0 | c >> 6;
					buf.append(HEX_TABLE[c1 >> 4], HEX_TABLE[c1 & 0xF]);

					buf.append('%');
					final int c2 = 0x80 | (c & 0x3F);
					buf.append(HEX_TABLE[c2 >> 4], HEX_TABLE[c2 & 0xF]);

					// unicode code point to utf8
//					u8 = 0xC000 | ((c >> 6) << 8);
//					u8 |= 0x80 | (c & 0x3F);

				} else if (c <= 0xFFFF) {
					final int c1 = 0xE0 | c >> 12;
					buf.append(HEX_TABLE[c1 >> 4], HEX_TABLE[c1 & 0xF]);

					buf.append('%');
					final int c2 = 0x80 | ((c >> 6) & 0x3F);
					buf.append(HEX_TABLE[c2 >> 4], HEX_TABLE[c2 & 0xF]);

					buf.append('%');
					final int c3 = 0x80 | (c & 0x3F);
					buf.append(HEX_TABLE[c3 >> 4], HEX_TABLE[c3 & 0xF]);

				} else {
					final int c1 = 0xF0 | c >> 18;
					buf.append(HEX_TABLE[c1 >> 4], HEX_TABLE[c1 & 0xF]);

					buf.append('%');

					final int c2 = 0x80 | ((c >> 12) & 0x3F);
					buf.append(HEX_TABLE[c2 >> 4], HEX_TABLE[c2 & 0xF]);

					buf.append('%');
					final int c3 = 0x80 | ((c >> 6) & 0x3F);
					buf.append(HEX_TABLE[c3 >> 4], HEX_TABLE[c3 & 0xF]);

					buf.append('%');
					final int c4 = 0x80 | (c & 0x3F);
					buf.append(HEX_TABLE[c4 >> 4], HEX_TABLE[c4 & 0xF]);
				}
			}
		}

		return new String(buf.buf, 0, buf.count);
	}
	
	public static String iriToURI(String uriPart) {
		String result = urlEncodeUtf8(uriPart);
		result = result.replaceAll("%23", "#");
		result = result.replaceAll("%2D", "-");
		result = result.replaceAll("%5F", "_");
		result = result.replaceAll("%2E", ".");
		result = result.replaceAll("%21", "!");
		result = result.replaceAll("%7E", "~");
		result = result.replaceAll("%2A", "*");
		result = result.replaceAll("%27", "'");
		result = result.replaceAll("%28", "(");
		result = result.replaceAll("%29", ")");
		result = result.replaceAll("%3B", ";");
		result = result.replaceAll("%2F", "/");
		result = result.replaceAll("%3F", "?");		
		result = result.replaceAll("%3A", ":");
		result = result.replaceAll("%40", "@");
		result = result.replaceAll("%26", "&");
		result = result.replaceAll("%3D", "=");		
		result = result.replaceAll("%2B", "+");
		result = result.replaceAll("%24", "\\$");
		result = result.replaceAll("%2C", ",");		
		result = result.replaceAll("%5B", "[");
		result = result.replaceAll("%5D", "]");		
		result = result.replaceAll("%25", "%");
		return result;
	}
	
	public static String escapeHtmlURI(String uri){
		String result = urlEncodeUtf8(uri);
		//TODO : to be continued
		result = result.replaceAll("\\+", " ");
		result = result.replaceAll("%20", " ");
		result = result.replaceAll("%23", "#");
		result = result.replaceAll("%2D", "-");
		result = result.replaceAll("%5F", "_");
		result = result.replaceAll("%2E", ".");
		result = result.replaceAll("%21", "!");
		result = result.replaceAll("%7E", "~");
		result = result.replaceAll("%2A", "*");
		result = result.replaceAll("%27", "'");
		result = result.replaceAll("%28", "(");
		result = result.replaceAll("%29", ")");
		result = result.replaceAll("%3B", ";");
		result = result.replaceAll("%2F", "/");
		result = result.replaceAll("%3F", "?");		
		result = result.replaceAll("%3A", ":");
		result = result.replaceAll("%40", "@");
		result = result.replaceAll("%26", "&");
		result = result.replaceAll("%3D", "=");		
		result = result.replaceAll("%2B", "+");
		result = result.replaceAll("%24", "\\$");
		result = result.replaceAll("%2C", ",");		
		result = result.replaceAll("%5B", "[");
		result = result.replaceAll("%5D", "]");		
		result = result.replaceAll("%25", "%");		
		return result;
	}
	
	/**
	 * This method is a wrapper for {@link java.net.URLEncoder#encode(java.lang.String,java.lang.String)}
	 * It calls this method, suppying the url parameter as
	 * the first parameter, and "UTF-8" (the W3C recommended
	 * encoding) as the second.  UnsupportedEncodingExceptions
	 * are wrapped in a runtime exception.
	 * 
	 * IMPORTANT: the java.net.URLEncoder class encodes a space (" ")
	 * as a "+".  The proper method of encoding spaces in the path of
	 * a URI is with "%20", so this method will replace all instances of "+"
	 * in the encoded string with "%20" before returning.  This means that
	 * XmldbURIs constructed from java.net.URLEncoder#encoded strings
	 * will not be String equivalents of XmldbURIs created with the result of
	 * calls to this function.
	 * 
	 * @param uri The uri to encode
	 * @return The UTF-8 encoded value of the supplied uri
	 */
	public static String urlEncodeUtf8(String uri) {
		try {
			final String almostEncoded = URLEncoder.encode(uri, "UTF-8");
			return almostEncoded.replaceAll("\\+","%20");
		} catch(final UnsupportedEncodingException e) {
			//wrap with a runtime Exception
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * This method decodes the provided uri for human readability.  The
	 * method simply wraps URLDecoder.decode(uri,"UTF-8).  It is places here
	 * to provide a friendly way to decode URIs encoded by urlEncodeUtf8()
	 * 
	 * @param uri The uri to decode
	 * @return The decoded value of the supplied uri
	 */
	public static String urlDecodeUtf8(String uri) {
		try {
			return URLDecoder.decode(uri, "UTF-8");
		} catch(final UnsupportedEncodingException e) {
			//wrap with a runtime Exception
			throw new RuntimeException(e);
		}
	}

	/**
	 * This method decodes the provided uri for human readability.  The
	 * method simply wraps URLDecoder.decode(uri,"UTF-8).  It is places here
	 * to provide a friendly way to decode URIs encoded by urlEncodeUtf8()
	 * 
	 * @param uri The uri to decode
	 * @return The decoded value of the supplied uri
	 */
	public static String urlDecodeUtf8(XmldbURI uri) {
		try {
			return URLDecoder.decode(uri.toString(), "UTF-8");
		} catch(final UnsupportedEncodingException e) {
			//wrap with a runtime Exception
			throw new RuntimeException(e);
		}
	}

	/**
	 * This method splits the supplied url on the character
	 * '/' then URL encodes the segments between, returning
	 * a URL encoded version of the passed url, leaving any
	 * occurrence of '/' as it is.
	 * 
	 * @param url The path to encode
	 * @return A UTF-8 URL encoded string
	 */
	public static String urlEncodePartsUtf8(String url) {
		final String[] split = url.split("/",-1);
		final StringBuilder ret = new StringBuilder(url.length());
		for(int i=0;i<split.length;i++) {
			ret.append(urlEncodeUtf8(split[i]));
			if(i<split.length-1) {
				ret.append("/");
			}
		}
		return ret.toString();
	}
	
	/**
	 * This method ensure that a collection path (e.g. /db/[])
	 * is properly URL encoded.  Uses W3C recommended UTF-8
	 * encoding.
	 * 
	 * @param path The path to check
	 * @return A UTF-8 URL encoded string
	 */
	public static String ensureUrlEncodedUtf8(String path) {
		try {
			final XmldbURI uri = XmldbURI.xmldbUriFor(path);
			return uri.getRawCollectionPath();
		} catch (final URISyntaxException e) {
			return URIUtils.urlEncodePartsUtf8(path);
		}
	}

	/**
	 * This method creates an <code>XmldbURI</code> by encoding the provided
	 * string, then calling XmldbURI.xmldbUriFor(String) with the result of that
	 * encoding
	 * 
	 * @param path The path to encode and create an XmldbURI from
	 * @return A UTF-8 URI encoded string
	 * @throws URISyntaxException A URISyntaxException is thrown if the path
	 * cannot be parsed by XmldbURI, after being encoded by
	 * <code>urlEncodePartsUtf8</code>
	 */
	public static XmldbURI encodeXmldbUriFor(String path) throws URISyntaxException {
		return XmldbURI.xmldbUriFor(URIUtils.urlEncodePartsUtf8(path));
	}

}
