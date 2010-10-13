/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.config;

import java.lang.reflect.Method;

/**
 * Forward reference resolver universal implementation.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ReferenceImpl<R, O> implements Reference<R, O> {

	R resolver;
	String name;
	
	public ReferenceImpl(R resolver, String name) {
		this.resolver = resolver;
		this.name = name;
	}
	
	O cached = null;
	
	@Override
	public O resolve() {
		if (cached == null) {
			String methodName = "get"+cached.getClass().getName();
			methodName = methodName.toLowerCase();
			
			Class<? extends Object> clazz = resolver.getClass();
			
			for (Method method : clazz.getMethods()) {
				if (method.getName().toLowerCase().equals(methodName)
						&& method.getParameterTypes().length == 1
						&& method.getParameterTypes()[0].getName().equals("java.lang.String")
					)
					try {
						cached = (O) method.invoke(resolver, name);
						break;
					} catch (Exception e) {
						cached = null;
					}

			}

			
		}
		return cached;
	}

	@Override
	public R resolver() {
		return resolver;
	}
}
