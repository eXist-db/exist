/*
 *  eXist Open Source Native XML Database
 * 
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class Step extends AbstractExpression {

    protected final static Logger LOG = LogManager.getLogger(Step.class);

    protected int axis = Constants.UNKNOWN_AXIS;

    protected boolean abbreviatedStep = false;

    protected List<Predicate> predicates = new CopyOnWriteArrayList<Predicate>();

    protected NodeTest test;

    protected boolean inPredicate = false;

    protected int staticReturnType = Type.ITEM;

    protected boolean hasPositionalPredicate = false;

    public Step( XQueryContext context, int axis ) {
        super(context);
        this.axis = axis;
    }

    public Step( XQueryContext context, int axis, NodeTest test ) {
        this( context, axis );
        this.test = test;
    }

    public void addPredicate( Expression expr ) {
        predicates.add( (Predicate) expr );
    }

    public void insertPredicate(Expression previous, Expression predicate) {
        final int idx = predicates.indexOf(previous);
        if (idx < 0) {
            LOG.warn("Old predicate not found: " + ExpressionDumper.dump(previous) + "; in: " + ExpressionDumper.dump(this));
            return;
        }
        predicates.add(idx + 1, (Predicate) predicate);
    }

    public boolean hasPredicates() {
        return predicates.size() > 0;
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        if (test != null && test.getName() != null &&
                test.getName().getPrefix() != null &&
                (!test.getName().getPrefix().isEmpty()) && context.getInScopePrefixes() !=  null &&
                context.getURIForPrefix(test.getName().getPrefix()) == null)
            {throw new XPathException(this, ErrorCodes.XPST0081, "undeclared prefix '"
                + test.getName().getPrefix() + "'");}
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        this.contextId = contextInfo.getContextId();
        if (predicates.size() > 0) {
            final AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
            newContext.setStaticType(this.axis == Constants.SELF_AXIS ? contextInfo.getStaticType() : Type.NODE);
            newContext.setParent(this);
            newContext.setContextStep(this);
            for (final Predicate pred : predicates) {
                pred.analyze(newContext);
            }
            if (predicates.size() == 1 && (newContext.getFlags() & POSITIONAL_PREDICATE) != 0)
                {hasPositionalPredicate = true;}
        }
        // if we are on the self axis, remember the static return type given in the context
        if (this.axis == Constants.SELF_AXIS)
            {staticReturnType = contextInfo.getStaticType();}
    }

    public abstract Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException;

    public int getAxis() {
        return axis;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
     */
    public void setPrimaryAxis(int axis) {
        this.axis = axis;
    }

    public int getPrimaryAxis() {
        return this.axis;
    }

    public boolean isAbbreviated() {
        return abbreviatedStep;
    }

    public void setAbbreviated(boolean abbrev) {
        abbreviatedStep = abbrev;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if (axis != Constants.UNKNOWN_AXIS)
            {dumper.display( Constants.AXISSPECIFIERS[axis] );}
        dumper.display( "::" );
        if ( test != null )
            //TODO : toString() or... dump ?
            {dumper.display( test.toString() );}
        else
            {dumper.display( "node()" );}
        if ( predicates.size() > 0 )
            for (final Predicate pred : predicates) {
                pred.dump(dumper);
            }
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        if (axis != Constants.UNKNOWN_AXIS)
            {result.append( Constants.AXISSPECIFIERS[axis] );}
        result.append( "::" );
        if (test != null )
            {result.append( test.toString() );}
        else
            {result.append( "node()" );}
        if (predicates.size() > 0 )
            for (final Predicate pred : predicates) {
                result.append(pred.toString());
            }
        return result.toString();
    }

    public int returnsType() {
        //Polysemy of "." which might be atomic if the context sequence is atomic itself
        if (axis == Constants.SELF_AXIS) {
            //Type.ITEM by default : this may change *after* evaluation
            return staticReturnType;
        } else
            {return Type.NODE;}
    }

    public int getCardinality() {
        return Cardinality.ZERO_OR_MORE;
   }

    public void setAxis( int axis ) {
        this.axis = axis;
    }

    public void setTest( NodeTest test ) {
        this.test = test;
    }

    public NodeTest getTest() {
        return test;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        for (final Predicate pred : predicates) {
            pred.resetState(postOptimization);
        }
    }
}