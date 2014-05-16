/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.expression;

import org.exist.xslt.XSLContext;

/**
 * Top-level elements fall into two categories: declarations, and user-defined data elements. 
 * Top-level elements whose names are in the XSLT namespace are declarations. 
 * Top-level elements in any other namespace are user-defined data elements.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class Declaration extends XSLPathExpr {

	public Declaration(XSLContext context) {
		super(context);
	}

    public void add(SimpleConstructor s) {
    	if (s instanceof Text) {
			return; //ignore text nodes
		}
        steps.add(s);
    }
}
