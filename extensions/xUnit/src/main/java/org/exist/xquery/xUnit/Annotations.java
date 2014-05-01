/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.xquery.xUnit;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.exist.xquery.Annotation;
import org.exist.xquery.AnnotationTrigger;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Annotations extends org.exist.xquery.Annotations {
	
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xUnit";
    //public final static String PREFIX = "test";
    private final static String RELEASED_IN_VERSION = "eXist-2.0";
    private final static String DESCRIPTION = "xUnit annotations";

    private final static Map<String, Class<? extends AnnotationTrigger>> anns = 
		new HashMap<String, Class<? extends AnnotationTrigger>>();
    
    static {
    	anns.put(AssertEquals.name, AssertEquals.class);
    	
    	org.exist.xquery.Annotations.register(NAMESPACE_URI, new Annotations());
    }
    
    public AnnotationTrigger getTrigger(String name, Annotation ann) {
    	Class<? extends AnnotationTrigger> clazz = anns.get(name);
    	
    	if (clazz == null) return null;
    	
    	try {
    		Constructor<? extends AnnotationTrigger> cnst = clazz.getConstructor(Annotation.class);
    		return cnst.newInstance(ann);

		} catch (Exception e) {
			// TODO log
			return null;
		}
    	
    }

    //@Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    //@Override
//    public String getDefaultPrefix() {
//        return PREFIX;
//    }

    //@Override
    public String getDescription() {
        return DESCRIPTION;
    }

    //@Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
