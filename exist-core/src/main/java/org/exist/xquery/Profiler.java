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

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

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
    public static final int TIME = 1;
    /** value for Verbosity property: For optimizations */
    public static final int OPTIMIZATIONS = 2;
    /** For computations that will trigger further optimizations */
    public static final int OPTIMIZATION_FLAGS = 3;
    /** Indicates the dependencies of the expression */
    public static final int DEPENDENCIES = 4;
    /** An abstract level for viewing the expression's context sequence/item */
    public static final int START_SEQUENCES = 4;
    /** Just returns the number of items in the sequence */
    public static final int ITEM_COUNT = 5;
    /** For a truncated string representation of the context sequence (TODO) */
    public static final int SEQUENCE_PREVIEW = 6;
    /** For a full representation of the context sequence (TODO) */
    public static final int SEQUENCE_DUMP = 8;

    public static final String CONFIG_PROPERTY_TRACELOG = "xquery.profiling.tracelog";

    /**
     * The logger where all output goes.
     */
    private Logger log = LogManager.getLogger("xquery.profiling");
    
    private Deque<ProfiledExpr> stack = new ArrayDeque<>();
    
    private final StringBuilder buf = new StringBuilder(64);
    
    private boolean enabled = false;

    private boolean logEnabled = false;
    
    private int verbosity = 0; 

    private PerformanceStats stats;

    private long queryStart = 0;

    private Database db;

    public Profiler(@Nullable final Database db) {
        this.db = db;

        boolean performanceStatsEnabled = false;
        if (db != null) {
            final String xqueryProfilingTraceEnabled = (String) db.getConfiguration().getProperty(PerformanceStatsImpl.CONFIG_PROPERTY_TRACE);
            performanceStatsEnabled = ("yes".equals(xqueryProfilingTraceEnabled) || "functions".equals(xqueryProfilingTraceEnabled));
        }
        this.stats = new PerformanceStatsImpl(performanceStatsEnabled, locallyEnabled -> locallyEnabled || (db != null && db.getPerformanceStats().isEnabled()));
    }

    /**
     * Configure the profiler from an XQuery option.
     * Parameters are:
     * 
     * <ul>
     *  <li><strong>enabled</strong>: yes|no.</li>
     *  <li><strong>logger</strong>: name of the logger to use.</li>
     *  <li><strong>verbosity</strong>: integer value &gt; 0. 1 does only output function calls.</li>
     * </ul>
     * @param pragma the option to read settings from
     */
    public final void configure(Option pragma) {
        final String options[] = pragma.tokenizeContents();
        String params[];
        for (String option : options) {
            params = Option.parseKeyValuePair(option);
            if (params != null) {
                if ("trace".equals(params[0])) {
                    stats.setEnabled(true);

                } else if ("tracelog".equals(params[0])) {
                    logEnabled = "yes".equals(params[1]);

                } else if ("logger".equals(params[0])) {
                    log = LogManager.getLogger(params[1]);

                } else if ("enabled".equals(params[0])) {
                    enabled = "yes".equals(params[1]);

                } else if ("verbosity".equals(params[0])) {
                    try {
                        verbosity = Integer.parseInt(params[1]);
                    } catch (final NumberFormatException e) {
                        log.warn("invalid value for verbosity: " +
                                "should be an integer between 0 and " +
                                SEQUENCE_DUMP);
                    }
                }
            }
        }
        if (verbosity == 0) 
            {enabled=false;}
    }
    
    /**
     * Is profiling enabled?
     * 
     * @return True if profiling is enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    public final boolean isLogEnabled() {
        try {
            final DBBroker broker = db.getActiveBroker();
            final Boolean globalProp = (Boolean) broker.getConfiguration().getProperty(CONFIG_PROPERTY_TRACELOG);
            return logEnabled || (globalProp != null && globalProp);
        } catch (Throwable t) {
            log.debug("Ignored exception: {}", t.getMessage());
            return logEnabled;
        }
    }

    public final void setLogEnabled(boolean enabled) {
        logEnabled = enabled;
    }
    
    public final boolean traceFunctions() {
        return stats.isEnabled() || isLogEnabled();
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
        stats.recordQuery(context.getSource().pathOrShortIdentifier(), (System.currentTimeMillis() - queryStart));
    }

    public final void traceFunctionStart(Function function) {
        if (isLogEnabled()) {
            log.trace(String.format("ENTER %-25s", function.getSignature().getName()));
        }
    }

    public final void traceFunctionEnd(Function function, long elapsed) {
        if (stats.isEnabled()) {
            final Source source = function.getContext().getSource();
            final String sourceMsg;
            if (source == null) {
                sourceMsg = String.format("[unknown source] [%d:%d]", function.getLine(), function.getColumn());
            } else {
                sourceMsg = String.format("%s [%d:%d]", function.getContext().getSource().pathOrShortIdentifier(),
                        function.getLine(), function.getColumn());
            }
            stats.recordFunctionCall(function.getSignature().getName(), sourceMsg, elapsed);
        }
        if (isLogEnabled()) {
            log.trace(String.format("EXIT  %-25s %10d ms", function.getSignature().getName(), elapsed));
        }
    }

    public final void traceIndexUsage(XQueryContext context, String indexType, Expression expression, PerformanceStats.IndexOptimizationLevel indexOptimizationLevel, long elapsed) {
        stats.recordIndexUse(expression, indexType, context.getSource().pathOrShortIdentifier(), indexOptimizationLevel, elapsed);
    }

    public final void traceOptimization(XQueryContext context, PerformanceStats.OptimizationType type, Expression expression) {
        stats.recordOptimization(expression, type, context.getSource().pathOrShortIdentifier());
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
            {return;}
        
        if (stack.isEmpty()) {
            log.debug("QUERY START");                
        }
        
        buf.setLength(0); 
    	for (int i = 0; i < stack.size(); i++) {
            buf.append('\t');
        }
        
        final ProfiledExpr e = new ProfiledExpr(expr);
    	stack.push(e);
            
        buf.append("START\t");
        printPosition(e.expr);                        
        buf.append(expr.toString()); 
        log.debug(buf.toString());

        if (message != null && !message.isEmpty()) {
            buf.setLength(0);
	    	for (int i = 0; i < stack.size(); i++) {
                buf.append('\t');
            }
            buf.append("MSG\t");
            buf.append(message);
            buf.append("\t");
            printPosition(e.expr);
            buf.append(expr);
            log.debug(buf.toString());
        }         
    }
    
    /**
     * Called by an expression to indicate the end of an operation.
     * The profiler computes the elapsed time.
     * 
     * @param expr the expression.
     * @param message required: a message to be printed to the log.
     * @param result the result sequence of the expression. If present and set to verbose output,
     *               the number of items in the result sequence will be printed to the log.
     */
    public final void end(Expression expr, String message, Sequence result) {
        if (!enabled)
            {return;}        
        
        try {         	     	
			final ProfiledExpr e = stack.pop(); 
		
			if (e.expr != expr) {
			    log.warn("Error: the object passed to end() does not correspond to the expression on top of the stack.");
			    stack.clear();
			    return;
			}
            
            final long elapsed = System.currentTimeMillis() - e.start;
            
            if (message != null && !message.isEmpty()) {
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++) {
                    buf.append('\t');
                }
    	    	buf.append("MSG\t");
                buf.append(message);
                buf.append("\t");
                printPosition(e.expr);
                buf.append(expr.toString());                 
                log.debug(buf.toString());
            } 
            
            if (verbosity > START_SEQUENCES) {
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++) {
                    buf.append('\t');
                }
                buf.append("RESULT\t");               
                /* if (verbosity >= SEQUENCE_DUMP) 
                    buf.append(result.toString());               
                else if (verbosity >= SEQUENCE_PREVIEW)
                    buf.append(sequencePreview(result));
                else*/ if (verbosity >= ITEM_COUNT) 
                    {
                        buf.append(result.getItemCount()).append(" item(s)");}
                buf.append("\t");     
                printPosition(e.expr);   
                buf.append(expr.toString());
                log.debug(buf.toString()); 
            }
            
            if (verbosity >= TIME) {   
                buf.setLength(0);
    	    	for (int i = 0; i < stack.size(); i++) {
                    buf.append('\t');
                }
                buf.append("TIME\t");                
                buf.append(elapsed).append(" ms");
                buf.append("\t");     
                printPosition(e.expr);   
                buf.append(expr.toString());                
                log.debug(buf.toString()); 
            }
			
            buf.setLength(0);
            for (int i = 0; i < stack.size(); i++) {
                buf.append('\t');
            }
            buf.append("END\t");
            printPosition(e.expr);            
            buf.append(expr.toString()); 
            log.debug(buf.toString());
            
            if (stack.isEmpty()) {
                log.debug("QUERY END");                
            }
		} catch (final RuntimeException e) {
            log.debug("Profiler: could not pop from expression stack - {} - {}. Error : {}", expr, message, e.getMessage());
		}
    }

    /**
     * Print out a single profiling message for the given 
     * expression object.
     * 
     * 
     * @param level debug level: message will be ignore if verbosity is set to lower level
     * @param title a title string
     * @param sequence the result sequence of the expression. If present and set to verbose output,
     *                 the number of items in the result sequence will be printed to the log.
     * @param expr the expression
     */
    public final void message(Expression expr, int level, String title, Sequence sequence) {
    	if (!enabled)
    		{return;}
        if (level > verbosity)
            {return;}
    	
    	buf.setLength(0);  
    	for (int i = 0; i < stack.size() - 1; i++) {
            buf.append('\t');
        }
        if (title != null && !title.isEmpty())
            {buf.append(title);}
        else
            {buf.append("MSG");}        
        buf.append("\t");        
        /* if (verbosity >= SEQUENCE_DUMP) 
            buf.append(sequence.toString()); 
        else if (verbosity >= SEQUENCE_PREVIEW)
            buf.append(sequencePreview(sequence));
        else */ if (verbosity >= ITEM_COUNT) 
            {
                buf.append(sequence.getItemCount()).append(" item(s)");}
        buf.append("\t"); 
        buf.append(expr.toString());        
    	log.debug(buf.toString());        
    }
    
    public final void message(Expression expr, int level, String title, String message) {
        if (!enabled)            
            {return;}
        if (level > verbosity)
            {return;}        
        
        buf.setLength(0);
    	for (int i = 0; i < stack.size() - 1; i++) {
            buf.append('\t');
        }
        if (title != null && !title.isEmpty())
            {buf.append(title);}
        else
            {buf.append("MSG");}        
        if (message != null && !message.isEmpty()) {
            buf.append("\t");
            buf.append(message);        	
        }
        buf.append("\t");
        printPosition(expr); 
        buf.append(expr);
        log.debug(buf.toString());
    }    
    
    public void reset() {
        if (!stack.isEmpty()) {
            log.debug("QUERY RESET");
        }

        stack.clear();

        if (stats.isEnabled()) {
            // merge these stats into the central BrokerPool stats
            if (db != null) {
                final PerformanceStats brokerPoolPerformanceStats = db.getPerformanceStats();
                brokerPoolPerformanceStats.recordAll(stats);
            }

            stats.reset();
        }
    }
    
    private void printPosition(Expression expr) {
        if (expr.getLine() > -1) {
            buf.append('[');
            buf.append(expr.getLine());
            buf.append(',');
            buf.append(expr.getColumn());
            buf.append("]\t");
        }
        else
            {buf.append("\t");}
    }
    
    //TODO : find a way to preview "abstract" sequences
    // never used locally
    @SuppressWarnings("unused")
	private String sequencePreview(Sequence sequence) {
        final StringBuilder truncation = new StringBuilder();
        if (sequence.isEmpty())
            {truncation.append(sequence);}
        else if (sequence.hasOne()) {
            truncation.append("(");            
            if (sequence.itemAt(0).toString().length() > 20) 
                {truncation.append(sequence.itemAt(0).toString().substring(0, 20)).append("... ");} 
            else
                {truncation.append(sequence.itemAt(0).toString());}            
            truncation.append(")");        
        } else  {
            truncation.append("(");
            if (sequence.itemAt(0).toString().length() > 20) 
                {truncation.append(sequence.itemAt(0).toString().substring(0, 20)).append("... ");} 
            else
                {truncation.append(sequence.itemAt(0).toString());}
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
