/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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

package org.exist.security.xacml;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.source.*;
import org.exist.xmldb.XmldbURI;

/**
 * This class represents the source of some content.  It has
 * a key, which uniquely identifies this source within its type.
 * For possible type values, see {@link XACMLConstants XACMLConstants}.
 */
public class XACMLSource
{
    private final static Logger LOG = LogManager.getLogger(XACMLSource.class);

	private static final String FILE_PROTOCOL = "file";
	private final String type;
	private final String key;
	
	public XACMLSource() { this(null, null); }
	public XACMLSource(String type, String key)
	{
		this.type = type;
		this.key = key;
	}
	
	public static XACMLSource getInstance(Class<?> source)
	{
		if(source == null)
			{throw new NullPointerException("Source class cannot be null");}
		return getInstance(source.getName());
	}
	public static XACMLSource getInstance(String sourceClassName)
	{
		if(sourceClassName == null)
			{throw new NullPointerException("Source class name cannot be null");}
		return new XACMLSource(XACMLConstants.CLASS_SOURCE_TYPE, sourceClassName);
	}
	public static XACMLSource getInstance(Source source)
	{
		if(source == null)
			{throw new NullPointerException("Source cannot be null");}
		if(source instanceof FileSource)
			{return new XACMLSource(XACMLConstants.FILE_SOURCE_TYPE, ((FileSource)source).getFilePath());}
		if(source instanceof URLSource)
		{
			final URL url = ((URLSource)source).getURL();
			final String protocol = url.getProtocol();
			final String host = url.getHost();
			if(protocol.equals(FILE_PROTOCOL) && (host == null || host.length() == 0 || "localhost".equals(host) || "127.0.0.1".equals(host)))
			{
				final String path = url.getFile();
				return new XACMLSource(XACMLConstants.FILE_SOURCE_TYPE, path);
			}
			final String key = url.toExternalForm();
			final String type = (source instanceof ClassLoaderSource) ? XACMLConstants.CLASSLOADER_SOURCE_TYPE : XACMLConstants.URL_SOURCE_TYPE; 
			return new XACMLSource(type, key);
		}
		if(source instanceof StringSource || source instanceof StringSourceWithMapKey)
			{return new XACMLSource(XACMLConstants.STRING_SOURCE_TYPE, XACMLConstants.STRING_SOURCE_TYPE);}

        // Cocoon classes are not on classpath during compile time.
        Class<?> class1;
        try {
            class1 = Class.forName("org.exist.source.CocoonSource");
            final Method method1 = class1.getMethod("getInstance", org.exist.source.Source.class);
            final Object o1 = method1.invoke(null, source);

            final Method method2 = class1.getMethod("getKey", (java.lang.Class<?>[]) null);
            final Object o2 = method2.invoke(o1, (Object[])null);


            final String key = (String) o2;
            LOG.info("Found CocoonSource with key " + key);

			return new XACMLSource(XACMLConstants.COCOON_SOURCE_TYPE, key);

        } catch (final Exception e) {
            // just continue
        } 

        
		if(source instanceof DBSource)
		{
			final XmldbURI key = ((DBSource)source).getDocumentPath();
			/*
			 * TODO: not sure what implications using toString here has, when the key
			 * is really an XmldbURI?
			 */
			return new XACMLSource(XACMLConstants.DB_SOURCE_TYPE, key.toString());
		}
		throw new IllegalArgumentException("Unsupported source type '" + source.getClass().getName() + "'");
	}
	
	public String getType()
	{
		return type;
	}
	public String getKey()
	{
		return key;
	}
	public String createId()
	{
		return type.equals(XACMLConstants.STRING_SOURCE_TYPE) ? "[constructed]" : (type + ": '" + key + "'");
	}
	public String toString() {
		return type+": "+key;
	}
}
