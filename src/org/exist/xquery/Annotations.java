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
package org.exist.xquery;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class Annotations {

    private final static Map<String, Annotations> ns = new HashMap<String, Annotations>();
    
    //workaround
    static {
    	try {
			final Class<?> clazz = Class.forName("org.exist.xquery.xUnit.Annotations");
			clazz.newInstance();
		} catch (final Exception e) {
		}
    }
    
    public static void register(String namespace, Annotations anns) {
    	ns.put(namespace, anns);
    }
    
    public static AnnotationTrigger getTrigger(Annotation ann) {
    	final Annotations anns = ns.get(ann.getName().getNamespaceURI());
    	
    	if (anns == null) {return null;}
    	
    	return anns.getTrigger(ann.getName().getLocalPart(), ann);
    }
    
    public abstract AnnotationTrigger getTrigger(String name, Annotation ann);

}
