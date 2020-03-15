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
package org.exist.xquery;

/**
 * Defines bit flags to indicate, upon which parts of the execution context an expression
 * depends ({@link org.exist.xquery.Expression#getDependencies()}).
 *  
 * @author wolf
 */
public class Dependency {
    
    public final static int UNKNOWN_DEPENDENCY = -1;

	/**
	 * Expression has no dependencies, for example, if it is a literal value.
	 */
    public final static int NO_DEPENDENCY = 0;
	
	/**
	 * Expression depends on the context sequence. This is the default
	 * for most expressions.
	 */
	public final static int CONTEXT_SET = 1;
	
	/**
	 * Expression depends on the current context item (in addition to the 
	 * context sequence).
	 */
	public final static int CONTEXT_ITEM = 2;
	
	/**
	 * Expression depends on a variable declared within the
	 * same for or let expression. 
	 */
	public final static int LOCAL_VARS = 4;
	
	/**
	 * Expression depends on a variable declared in the context, i.e.
	 * an outer let or for.
	 */
	public final static int CONTEXT_VARS = 8;
	
	/**
	 * Bit mask to test if the expression depends on a variable reference.
	 */
	public final static int VARS = LOCAL_VARS + CONTEXT_VARS;
	
	/**
	 * Expression evaluates the context position and thus requires
	 * that the corresponding field in the context is set.
	 */
	public final static int CONTEXT_POSITION = 16;
	
	/**
	 * The default dependencies: just CONTEXT_SET is set.
	 */
	public final static int DEFAULT_DEPENDENCIES = CONTEXT_SET;
	
	public final static String getDependenciesName(int dependencies) {
        if (dependencies == UNKNOWN_DEPENDENCY) 
            {return "UNKNOWN";}
        if (dependencies == NO_DEPENDENCY) 
            {return "NO_DEPENDENCY";}
		final StringBuilder result = new StringBuilder();
        result.append("[");        
        if ((dependencies & CONTEXT_SET) != 0) 
            {result.append("CONTEXT_SET | ");}
		if ((dependencies & CONTEXT_ITEM) != 0) 
			{result.append("CONTEXT_ITEM | ");}
		if ((dependencies & LOCAL_VARS) != 0) 
			{result.append("LOCAL_VARS | ");}
		if ((dependencies & CONTEXT_VARS) != 0) 
			{result.append("CONTEXT_VARS | ");}
		if ((dependencies & CONTEXT_POSITION) != 0) 
			{result.append("CONTEXT_POSITION | ");}	
		result.delete(result.length() - 3, result.length());
		result.append("]");
		return result.toString();
	}	
    
    public final static boolean dependsOn(Expression expr, int dependency) {
        return ((expr.getDependencies()& dependency) == dependency);
    }

    public static final boolean dependsOn(int expressionDep, int dependency) {
        return ((expressionDep & dependency) == dependency);
    }

    public final static boolean dependsOnVar(Expression expr) {
        return ((expr.getDependencies() & VARS) != 0);
    }
}
