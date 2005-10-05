/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

    /**
     * The logger where all output goes.
     */
    private Logger log = Logger.getLogger("xquery.profiling");
    
    private Stack stack = new Stack();
    
    private final StringBuffer buf = new StringBuffer(64);
    
    private long profilingThreshold = 5;
    
    private boolean enabled = false;
    
    private int verbosity = 0;
    
    private boolean queryStarted = false;
    
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
    public final void configure(Pragma pragma) {
        String options[] = pragma.tokenizeContents();
        String params[];
        for (int i = 0; i < options.length; i++) {
            params = Pragma.parseKeyValuePair(options[i]);
            if (params != null) {
                if (params[0].equals("logger"))
                    log = Logger.getLogger(params[1]);
                else if (params[0].equals("enabled"))
                    enabled = params[1].equals("yes");
                else if ("verbosity".equals(params[0])) {
                    try {
                        verbosity = Integer.parseInt(params[1]);
                    } catch (NumberFormatException e) {
                    }
                } else if("threshold".equals(params[0])) {
                    try {
                        profilingThreshold = Integer.parseInt(params[1]);
                    } catch(NumberFormatException e) {
                    }
                }
            }
        }
    }
    
    /**
     * Is profiling enabled?
     * 
     * @return
     */
    public final boolean isEnabled() {
        return enabled;
    }
    
    /**
     * @return the verbosity of the profiler.
     */
    public final int verbosity() {
        return verbosity;
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
        if (enabled) {
            ProfiledExpr e = new ProfiledExpr(expr);
            stack.push(e);
        
            if (!queryStarted) {
                log.debug("QUERY START");
                queryStarted = true;
            }
            
            if (message != null) {
                buf.setLength(0);
                printPosition(e);
                buf.append('\t');
                buf.append("START\t\t- ");
                buf.append(message);
                log.debug(buf.toString());
            }
        }
    }
    
    /**
     * Called by an expression to indicate the end of an operation.
     * The profiler computes the elapsed time.
     * 
     * @param expr the expression.
     * @param message required: a message to be printed to the log.
     */
    public final void end(Expression expr, String message) {
        if (!enabled)
            return;
        
        ProfiledExpr e = (ProfiledExpr) stack.pop();
        if (e.expr != expr) {
            log.warn("Error: the object passed to end() does not correspond to the expression on top of the stack.");
            stack.clear();
            return;
        }
        
        long elapsed = System.currentTimeMillis() - e.start;
        buf.setLength(0);
        printPosition(e);
        buf.append("\tEND\t");
        buf.append(elapsed);
        buf.append("ms - ");
        if (message != null)
            buf.append(message);
        log.debug(buf.toString());
    }

    public void reset() {
        queryStarted = false;
    }
    
    /**
     * @param e
     */
    private void printPosition(ProfiledExpr e) {
        XQueryAST ast = e.expr.getASTNode();
        if (ast != null) {
            buf.append('[');
            buf.append(ast.getColumn());
            buf.append(',');
            buf.append(ast.getLine());
            buf.append("] ");
        }
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
