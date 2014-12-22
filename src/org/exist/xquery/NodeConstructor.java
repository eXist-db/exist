/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for all node constructors.
 * 
 * @author wolf
 */
public abstract class NodeConstructor extends AbstractExpression {

    protected MemTreeBuilder builder = null;
    protected boolean newDocumentContext = false;

    public NodeConstructor(XQueryContext context) {
        super(context);
    }

    public void setDocumentBuilder(MemTreeBuilder builder) {
        this.builder = builder;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        newDocumentContext = (contextInfo.getFlags() & IN_NODE_CONSTRUCTOR) == 0;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public abstract Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#preselect(org.exist.dom.persistent.DocumentSet, org.exist.xquery.StaticContext)
     */
    public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
        return in_docs;
    }

    @Override
    public int getDependencies() {
        // if this is a top-level node constructor, it must depend on the context item, so
        // an expression like //foo/<x/> will generate one <x> for every foo. However, if the
        // constructor appears inside another constructor, we don't want it to be called once
        // for every context item, so we just return a dependency on context set:
        if (newDocumentContext)
            {return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;}
        else
            {return Dependency.CONTEXT_SET;}
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.Expression#returnsType()
    */
    public int returnsType() {
        return Type.NODE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
    }
}
