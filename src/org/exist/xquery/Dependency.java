package org.exist.xquery;

/**
 * Defines bit flags to indicate, upon which parts of the execution context an expression
 * depends ({@see org.exist.xquery.Expression#getDependencies()}).
 *  
 * @author wolf
 */
public class Dependency {

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

}
