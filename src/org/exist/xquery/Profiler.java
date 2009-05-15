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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.Stack;

import org.apache.log4j.Logger;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Sequence;
import org.exist.storage.BrokerPool;

/**
 * XQuery profiling output. Profiling information is written to a
 * logger. The profiler can be enabled/disabled and configured
 * via an XQuery pragma or "declare option" expression. Example:
 * 
 * <pre>declare option exist:profiling "enabled=yes verbosity=10 logger=profiler";</pre>
 * 
 * @author wolf
 *
 */
public class Profiler {

    /** value for Verbosity property: basic profiling : just elapsed time */
    public static int TIME = 1;
    /** value for Verbosity property: For optimizations */
    public static int OPTIMIZATIONS = 2;
    /** For computations that will trigger further optimizations */
    public static int OPTIMIZATION_FLAGS = 3;
    /** Indicates the dependencies of the expression */
    public static int DEPENDENCIES = 4;
    /** An abstract level for viewing the expression's context sequence/item */
    public static int START_SEQUENCES = 4;
    /** Just returns the number of items in the sequence */
    public static int ITEM_COUNT = 5;
    /** For a truncated string representation of the context sequence (TODO) */
    public static int SEQUENCE_PREVIEW = 6;
    /** For a full representation of the context sequence (TODO) */
    public static int SEQUENCE_DUMP = 8;


    /**
     * The logger where all output goes.
     */
    private Logger log = Logger.getLogger("xquery.profiling");
    
    private Stack stack = new Stack();
    
    private final StringBuffer buf = new StringBuffer(64);
    
    private boolean enabled = false;
    
    private int verbosity = 0; 

    private PerformanceStats stats;

    private Stack<Long> functionStack;

    private long queryStart = 0;

    private BrokerPool pool;

    public Profiler(BrokerPool pool) {
        this.pool = pool;
        this.stats = new PerformanceStats(pool);
        this.functionStack = new Stack<Long>();
    }

    /**
     * Configure the profiler from an XQuery pragma.
     * Parameters are:
     * 
     * <ul>
     *  <li><strong>enabled</strong>: yes|no.</li>
     *  <li><strong>logger</strong>: name of the logger to use.</li>
     *  <li><strong>verbosity</strong>: integer value &gt; 0. 1 does only output function calls.</li>
     * </ul>
     * @param pragma
     */
    public final void configure(Option pragma) {
        String options[] = pragma.tokenizeContents();
        String params[];
        for (int i = 0; i < options.length; i++) {
            params = Option.parseKeyValuePair(options[i]);
            if (params != null) {
                if (params[0].equals("trace")) {
                    stats.setEnabled(true);

                } else if (params[0].equals("logger")) {
                    log = Logger.getLogger(params[1]);
                
                } else if (params[0].equals("enabled")) {
                    enabled = params[1].equals("yes");
                
                } else if ("verbosity".equals(params[0])) {
                    try {
                        verbosity = Integer.parseInt(params[1]);
                    } catch (NumberFormatException e) {
                    	log.warn( "invalid value for verbosity: " +
                    			"should be an integer between 0 and " + 
                    			SEQUENCE_DUMP );                   	
                    }
                }
            }
        }
        if (verbosity == 0) 
            enabled=false;
    }
    
    /**
     * Is profiling enabled?
     * 
     * @return True if profiling is enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    public final boolean traceFunctions() {
        return stats.isEnabled();
    }
    
    /**
     * @return the verbosity of the profiler.
     */
    public final int verbosity() {
        return verbosity;
    }

    public final void traceQueryStart() {
        queryStart = System.currentTimeMillis();
    }

    public final void traceQueryEnd(XQueryContext context) {
        stats.recordQuery(context.getSourceKey(), (System.currentTimeMillis() - queryStart));
    }

    public final void traceFunctionStart(FunctionCall function) {
        functionStack.push(System.currentTimeMillis());
    }

    public final void traceFunctionEnd(FunctionCall function) {
        if (functionStack.isEmpty()) // may happen if profiling was enabled in the middle of a query
            return;
        long startTime = functionStack.pop();
        stats.recordFunctionCall(function.getSignature().getName(), function.getContext().getSourceKey(), 
            (System.currentTimeMillis() - startTime));
    }

    private void save() {
        if (pool != null) {
            pool.getPerformanceStats().merge(stats);
        }
    }

    /**
     * Called by an expression to indicate the start of an operation.
     * The profiler registers the start time.
     * 
     * @param expr the expression.
     */
    public final void start(Expression expr) {
        start(expr, null);
    }
    
    /**
     * Called by an expression to indicate the start of an operation.
     * The profiler registers the start time.
     * 
     * @param expr the expression.
     * @param message if not null, contains an optional message to print in the log.
     */
    public final void start(Expression expr, String message) {
        if (!enabled)
            return;
        
        if (stack.size() == 0) {
            log.debug("QUERY START");                
        }
        
        buf.setLength(0); 
    	for (int i = 0; i < stack.size(); i++)
    		buf.append('\t');             
        
        ProfiledExpr e = new ProfiledExpr(expr);
    	stack.push(e);
            
        buf.append("START\t");
        printPosition(e.expr);                        
        buf.append(expr.toString()); 
        log.debug(buf.toString());

        if (message != null && !"".equals(message)) {           
            buf.setLength(0);
	    	for (int i = 0; i < stack.size(); i++)
	    		buf.append('\t');	            
            buf.append("MSG\t");
            buf.append(message);
            buf.append("\t");
            printPosition(e.expr);
            buf.append(expr.toString());             
            log.debug(buf.toString());
        }         
    }
    
    /**
     * Called by an expression to indicate the end of an operation.
     * The profiler computes the elapsed time.
     * 
     * @param expr the expression.
     * @param message required: a message to be printed to the log.
     */
    public final void end(Expression expr, String message, Sequence result) {
        if (!enabled)
            return;        
        
        try {         	     	
			ProfiledExpr e = (ProfiledExpr) stack.pop(); 
		
			if (e.expr != expr) {
			    log.warn("Error: the object passed to end() does not correspond to the expression on top of the stack.");
			    stack.clear();
			    return;
			}
            
            long elapsed = System.currentTimeMillis() - e.start;
            
            if (message != null && !"".equals(message)) {                
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++)
    	    		buf.append('\t');	
    	    	buf.append("MSG\t");
                buf.append(message);
                buf.append("\t");
                printPosition(e.expr);
                buf.append(expr.toString());                 
                log.debug(buf.toString());
            } 
            
            if (verbosity > START_SEQUENCES) {
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++)
    	    		buf.append('\t');	            	
                buf.append("RESULT\t");               
                /* if (verbosity >= SEQUENCE_DUMP) 
                    buf.append(result.toString());               
                else if (verbosity >= SEQUENCE_PREVIEW)
                    buf.append(sequencePreview(result));
                else*/ if (verbosity >= ITEM_COUNT) 
                    buf.append(result.getItemCount() + " item(s)");   
                buf.append("\t");     
                printPosition(e.expr);   
                buf.append(expr.toString());
                log.debug(buf.toString()); 
            }
            
            if (verbosity >= TIME) {   
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++)
    	    		buf.append('\t');	            	
                buf.append("TIME\t");                
                buf.append(elapsed + " ms"); 
                buf.append("\t");     
                printPosition(e.expr);   
                buf.append(expr.toString());                
                log.debug(buf.toString()); 
            }
			
            buf.setLength(0);
            for (int i = 0; i < stack.size(); i++)
	    		buf.append('\t');	                    
            buf.append("END\t");
            printPosition(e.expr);            
            buf.append(expr.toString()); 
            log.debug(buf.toString());
            
            if (stack.size() == 0) {
                log.debug("QUERY END");                
            }
		} catch (RuntimeException e) {
			log.debug("Profiler: could not pop from expression stack - " + expr + " - "+ message + ". Error : "+ e.getMessage());
		}
    }

    /**
     * Print out a single profiling message for the given 
     * expression object.
     * 
     * 
     * @param level 
     * @param title 
     * @param sequence 
     * @param expr 
     */
    public final void message(Expression expr, int level, String title, Sequence sequence) {
    	if (!enabled)
    		return;
        if (level > verbosity)
            return;
    	
    	buf.setLength(0);  
    	for (int i = 0; i < stack.size() - 1; i++)
    		buf.append('\t');
        if (title != null && !"".equals(title))
            buf.append(title);
        else
            buf.append("MSG");        
        buf.append("\t");        
        /* if (verbosity >= SEQUENCE_DUMP) 
            buf.append(sequence.toString()); 
        else if (verbosity >= SEQUENCE_PREVIEW)
            buf.append(sequencePreview(sequence));
        else */ if (verbosity >= ITEM_COUNT) 
            buf.append(sequence.getItemCount() + " item(s)"); 
        buf.append("\t"); 
        buf.append(expr.toString());        
    	log.debug(buf.toString());        
    }
    
    public final void message(Expression expr, int level, String title, String message) {
        if (!enabled)            
            return;
        if (level > verbosity)
            return;        
        
        buf.setLength(0);
    	for (int i = 0; i < stack.size() - 1; i++)
    		buf.append('\t');        
        if (title != null && !"".equals(title))
            buf.append(title);
        else
            buf.append("MSG");        
        if (message != null && !"".equals(message)) {
            buf.append("\t");
            buf.append(message);        	
        }
        buf.append("\t");
        printPosition(expr); 
        buf.append(expr.toString());         
        log.debug(buf.toString());
    }    
    
    public void reset() {
        if (stack.size() > 0)
            log.debug("QUERY RESET");  
        stack.clear();
        if (stats.isEnabled() && stats.hasData()) {
            save();
            stats.reset();
        }
        functionStack.clear();
    }
    
    private void printPosition(Expression expr) {
        XQueryAST ast = expr.getASTNode();       
        if (ast != null) {
            buf.append('[');
            buf.append(ast.getColumn());
            buf.append(',');
            buf.append(ast.getLine());
            buf.append("]\t");
        }
        else
            buf.append("\t");
    }
    
    //TODO : find a way to preview "abstract" sequences
    // never used locally
    private String sequencePreview(Sequence sequence) {
        StringBuffer truncation = new StringBuffer();         
        if (sequence.isEmpty())
            truncation.append(sequence.toString());
        else if (sequence.hasOne()) {
            truncation.append("(");            
            if (sequence.itemAt(0).toString().length() > 20) 
                truncation.append(sequence.itemAt(0).toString().substring(0, 20)).append("... "); 
            else
                truncation.append(sequence.itemAt(0).toString());            
            truncation.append(")");        
        } else  {
            truncation.append("(");
            if (sequence.itemAt(0).toString().length() > 20) 
                truncation.append(sequence.itemAt(0).toString().substring(0, 20)).append("... "); 
            else
                truncation.append(sequence.itemAt(0).toString());
            truncation.append(", ... )"); 
        }                  
        return truncation.toString();
    }

    private final static class ProfiledExpr {
        long start;
        Expression expr;
        
        private ProfiledExpr(Expression expression) {
            this.expr = expression;
            this.start = System.currentTimeMillis();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVerbosity(int verbosity) {
        this.verbosity = verbosity;
    }
}
