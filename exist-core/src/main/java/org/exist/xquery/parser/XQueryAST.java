/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.parser;

import antlr.CommonAST;
import antlr.Token;
import antlr.collections.AST;


/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class XQueryAST extends CommonAST {
	
	private int line = 0;
	private int column = 0;
	
	public XQueryAST() {
		super();
	}
	
	public XQueryAST(int type, String text) {
		initialize(type, text);
	}
	
	public XQueryAST(AST ast) {
		initialize(ast);
	}
	
	
	/* (non-Javadoc)
	 * @see antlr.CommonAST#initialize(antlr.collections.AST)
	 */
	public void initialize(AST ast) {
		super.initialize(ast);
		if(ast instanceof XQueryAST) {
			copyLexInfo((XQueryAST)ast);
		}
	}
	
	
	/* (non-Javadoc)
	 * @see antlr.CommonAST#initialize(antlr.Token)
	 */
	public void initialize(Token token) {
		super.initialize(token);
		setLine(token.getLine());
		setColumn(token.getColumn());
	}
	
	public void copyLexInfo(XQueryAST ast) {
		setLine(ast.getLine());
		setColumn(ast.getColumn());
	}
	
	public void setLine(int line) {
		this.line = line;
	}
	
	public void setColumn(int column) {
		this.column = column;
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
	
	public void setDoc(String doc) {
        // implemented by subclasses
    }

    public String getDoc() {
        // might be implemented by subclasses
        return null;
    }

//	/* (non-Javadoc)
//	 * @see antlr.BaseAST#toString()
//	 */
//	public String toString() {
//		StringBuffer buf = new StringBuffer();
//		buf.append(super.toString());
//		buf.append("[");
//		buf.append(getLine());
//		buf.append(", ");
//		buf.append(getColumn());
//		buf.append("]");
//		return buf.toString();
//	}
}
