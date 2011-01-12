/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;


/**
 * Implements a dynamic namespace constructor.
 * 
 * @author wolf
 */
public class NamespaceConstructor extends NodeConstructor {

    final private String prefix;
    private Expression uri = null;
    
    /**
     * @param context
     */
    public NamespaceConstructor(XQueryContext context, String prefix) {
        super(context);
        this.prefix = prefix;
    }

    public void setURIExpression(Expression uriExpr) {
        this.uri = new Atomize(context, uriExpr);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        uri.analyze(contextInfo);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        MemTreeBuilder builder = context.getDocumentBuilder();
		context.proceed(this, builder);
        
        Sequence uriSeq = uri.eval(contextSequence, contextItem);
        String value;
        if(uriSeq.isEmpty())
            value = "";
        else {
            StringBuilder buf = new StringBuilder();
            for(SequenceIterator i = uriSeq.iterate(); i.hasNext(); ) {
                context.proceed(this, builder);
                Item next = i.nextItem();
                if(buf.length() > 0)
                    buf.append(' ');
                buf.append(next.toString());
            }
            value = buf.toString();
        }
        context.declareInScopeNamespace(prefix, value);
        int nodeNr = builder.namespaceNode(prefix, value);
        Sequence result = ((DocumentImpl)builder.getDocument()).getNamespaceNode(nodeNr);
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("namespace ").display(prefix);
        dumper.display("{");
        uri.dump(dumper);
        dumper.display("}");
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("namespace ").append(prefix);
    	result.append("{");
    	result.append(uri.toString());
    	result.append("}");
    	return result.toString();
    }   
    
    public void resetState(boolean postOptimization) {
    	super.resetState(postOptimization);
    	uri.resetState(postOptimization);
    }
}
