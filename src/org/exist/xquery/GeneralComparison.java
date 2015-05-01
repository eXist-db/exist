/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery;

import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.text.Collator;
import java.util.List;


/**
 * A general XQuery/XPath2 comparison expression.
 *
 * @author  wolf
 * @author  andrzej@chaeron.com
 */
public class GeneralComparison extends BinaryOp implements Optimizable, IndexUseReporter
{
    /** The type of operator used for the comparison, i.e. =, !=, &lt;, &gt; ... One of the constants declared in class {@link Constants}. */
    protected Comparison          relation              = Comparison.EQ;

    /**
     * Truncation flags: when comparing with a string value, the search string may be truncated with a single * wildcard. See the constants declared
     * in class {@link Constants}.
     *
     * <p>The standard functions starts-with, ends-with and contains are transformed into a general comparison with wildcard. Hence the need to
     * consider wildcards here.</p>
     */
    protected StringTruncationOperator          truncation            = StringTruncationOperator.NONE;

    /** The class might cache the entire results of a previous execution. */
    protected CachedResult cached                = null;

    /** Extra argument (to standard functions starts-with/contains etc.) to indicate the collation to be used for string comparisons. */
    protected Object       collationArg          = null;

    /** Set to true if this expression is called within the where clause of a FLWOR expression. */
    protected boolean      inWhereClause         = false;

    protected boolean      invalidNodeEvaluation = false;

    protected int          rightOpDeps;

    private boolean        hasUsedIndex          = false;

    @SuppressWarnings( "unused" )
    private int            actualReturnType = Type.ITEM;

    private LocationStep   contextStep      = null;
    private QName          contextQName     = null;
    protected boolean      optimizeSelf     = false;
    protected boolean      optimizeChild    = false;

    private int            axis             = Constants.UNKNOWN_AXIS;
    private NodeSet        preselectResult  = null;

    public GeneralComparison( XQueryContext context, Comparison relation )
    {
        this( context, relation, StringTruncationOperator.NONE );
    }


    public GeneralComparison( XQueryContext context, Comparison relation, StringTruncationOperator truncation )
    {
        super( context );
        this.relation = relation;
    }


    public GeneralComparison( XQueryContext context, Expression left, Expression right, Comparison relation )
    {
        this( context, left, right, relation, StringTruncationOperator.NONE );
    }


    public GeneralComparison( XQueryContext context, Expression left, Expression right, Comparison relation, final StringTruncationOperator truncation )
    {
        super(context);
        boolean didLeftSimplification  = false;
        boolean didRightSimplification = false;
        this.relation   = relation;
        this.truncation = truncation;

        if( ( left instanceof PathExpr ) && ( ( ( PathExpr )left ).getLength() == 1 ) ) {
            left                  = ( ( PathExpr )left ).getExpression( 0 );
            didLeftSimplification = true;
        }
        add(left);

        if( ( right instanceof PathExpr ) && ( ( ( PathExpr )right ).getLength() == 1 ) ) {
            right                  = ( ( PathExpr )right ).getExpression( 0 );
            didRightSimplification = true;
        }
        add(right);

        //TODO : should we also use simplify() here ? -pb
        if( didLeftSimplification ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Marked left argument as a child expression" );
        }

        if( didRightSimplification ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Marked right argument as a child expression" );
        }
    }

    @Override
    public void analyze( AnalyzeContextInfo contextInfo ) throws XPathException
    {
        contextInfo.addFlag( NEED_INDEX_INFO );
        contextInfo.setParent( this );
        super.analyze( contextInfo );
        inWhereClause         = ( contextInfo.getFlags() & IN_WHERE_CLAUSE ) != 0;

        //Ugly workaround for the polysemy of "." which is expanded as self::node() even when it is not relevant
        // (1)[.= 1] works...
        invalidNodeEvaluation = false;

        if( !Type.subTypeOf( contextInfo.getStaticType(), Type.NODE ) ) {
            invalidNodeEvaluation = ( getLeft() instanceof LocationStep ) && ( ( ( LocationStep )getLeft() ).axis == Constants.SELF_AXIS );
        }

        //Unfortunately, we lose the possibility to make a nodeset optimization
        //(we still don't know anything about the contextSequence that will be processed)

        // check if the right-hand operand is a simple cast expression
        // if yes, use the dependencies of the casted expression to compute
        // optimizations
        rightOpDeps = getRight().getDependencies();
        getRight().accept( new BasicExpressionVisitor() {
                public void visitCastExpr( CastExpression expression )
                {
                    if( LOG.isTraceEnabled() ) {
                        LOG.debug( "Right operand is a cast expression" );
                    }
                    rightOpDeps = expression.getInnerExpression().getDependencies();
                }
            } );

        contextInfo.removeFlag( NEED_INDEX_INFO );

        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps( getLeft() );

        if( !steps.isEmpty() ) {
            LocationStep firstStep = steps.get( 0 );
            LocationStep lastStep  = steps.get( steps.size() - 1 );

            if( firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();

                if( ( outerExpr != null ) && ( outerExpr instanceof LocationStep ) ) {
                    final LocationStep outerStep = ( LocationStep )outerExpr;
                    final NodeTest     test      = outerStep.getTest();

                    if( !test.isWildcardTest() && ( test.getName() != null ) ) {


                        if( ( outerStep.getAxis() == Constants.ATTRIBUTE_AXIS ) || ( outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS ) ) {
                            contextQName = new QName(test.getName(), ElementValue.ATTRIBUTE);
                        } else {
                            contextQName = new QName(test.getName());
                        }
                        contextStep  = firstStep;
                        axis         = outerStep.getAxis();
                        optimizeSelf = true;
                    }
                }
            } else if (firstStep != null && lastStep != null) {
                final NodeTest test = lastStep.getTest();

                if( !test.isWildcardTest() && ( test.getName() != null ) ) {


                    if( ( lastStep.getAxis() == Constants.ATTRIBUTE_AXIS ) || ( lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS ) ) {
                        contextQName = new QName( test.getName(), ElementValue.ATTRIBUTE );
                    } else {
                        contextQName = new QName( test.getName() );
                    }
                    contextStep = lastStep;
                    axis        = firstStep.getAxis();

                    if( ( axis == Constants.SELF_AXIS ) && ( steps.size() > 1 ) ) {
                    	if (steps.get(1) != null) {
                    		axis = steps.get( 1 ).getAxis();
                    	} else {
                    		contextQName = null;
                    		contextStep = null;
                    		axis = Constants.UNKNOWN_AXIS;
                    		optimizeChild = false;
                    	}
                    }
                    optimizeChild = ( steps.size() == 1 ) && ( ( axis == Constants.CHILD_AXIS ) || ( axis == Constants.ATTRIBUTE_AXIS ) );
                }
            }
        }
    }

    @Override
    public boolean canOptimize(final Sequence contextSequence) {
        return false;
    }

    @Override
    public boolean optimizeOnSelf()
    {
        return( optimizeSelf );
    }

    @Override
    public boolean optimizeOnChild()
    {
        return( optimizeChild );
    }

    @Override
    public int getOptimizeAxis()
    {
        return( axis );
    }


    @Override
    public int returnsType()
    {
        if( inPredicate && ( !Dependency.dependsOn( this, Dependency.CONTEXT_ITEM ) ) ) {
            return( getLeft().returnsType() );
        }

        // In all other cases, we return boolean
        return( Type.BOOLEAN );
    }


    @Override
    public int getDependencies()
    {
        final Expression left = getLeft();

        // variable dependencies should be reported to caller, so remember them here
        final int deps = left.getDependencies() & Dependency.VARS;

        // left expression returns node set
        if( Type.subTypeOf( left.returnsType(), Type.NODE ) &&
                //  and does not depend on the context item
                !Dependency.dependsOn( left, Dependency.CONTEXT_ITEM ) && ( !inWhereClause || !Dependency.dependsOn( left, Dependency.CONTEXT_VARS ) ) ) {
            return( deps + Dependency.CONTEXT_SET );
        } else {
            return ( deps + Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM );
        }
    }


    public Comparison getRelation()
    {
        return( this.relation );
    }

    public StringTruncationOperator getTruncation() {
        return truncation;
    }

    @Override
    public NodeSet preSelect( final Sequence contextSequence, final boolean useContext ) throws XPathException
    {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        final long start     = System.currentTimeMillis();

        final Sequence rightSeq = getRight().eval( contextSequence );
        
        // if the right hand sequence has more than one item, we need to merge them
        // into preselectResult
        if (rightSeq.getItemCount() > 1) {
            preselectResult = new NewArrayNodeSet();
        }

        return( ( preselectResult == null ) ? NodeSet.EMPTY_SET : preselectResult );
    }


    @Override
    public Sequence eval( Sequence contextSequence, final Item contextItem ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().start(this);
            context.getProfiler().message( this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName( this.getDependencies() ) );

            if( contextSequence != null ) {
                context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence );
            }

            if( contextItem != null ) {
                context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence() );
            }
        }

        Sequence result;

        // if the context sequence hasn't changed we can return a cached result
        if( ( cached != null ) && cached.isValid( contextSequence, contextItem ) ) {
            LOG.debug( "Using cached results" );

            if( context.getProfiler().isEnabled() ) {
                context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Returned cached result" );
            }
            result = cached.getResult();

        } else {

            // if we were optimizing and the preselect did not return anything,
            // we won't have any matches and can return
            if( ( preselectResult != null ) && preselectResult.isEmpty() ) {
                result = Sequence.EMPTY_SEQUENCE;
            } else {

                if( ( contextStep == null ) || ( preselectResult == null ) ) {

                    /*
                     * If we are inside a predicate and one of the arguments is a node set,
                     * we try to speed up the query by returning nodes from the context set.
                     * This works only inside a predicate. The node set will always be the left
                     * operand.
                     */
                    if( inPredicate && !invalidNodeEvaluation && !Dependency.dependsOn( this, Dependency.CONTEXT_ITEM ) && Type.subTypeOf( getLeft().returnsType(), Type.NODE ) && ( ( contextSequence == null ) || contextSequence.isPersistentSet() ) ) {

                        if( contextItem != null ) {
                            contextSequence = contextItem.toSequence();
                        }

                        if( ( !Dependency.dependsOn( rightOpDeps, Dependency.CONTEXT_ITEM ) ) ) {
                            result = quickNodeSetCompare( contextSequence );
                        } else {
                            final NodeSet nodes = ( NodeSet )getLeft().eval( contextSequence );
                            result = nodeSetCompare( nodes, contextSequence );
                        }
                    } else {
                        result = genericCompare( contextSequence, contextItem );
                    }
                } else {
                    contextStep.setPreloadedData( preselectResult.getDocumentSet(), preselectResult );
                    result = getLeft().eval( contextSequence ).toNodeSet();
                    // the expression can be called multiple times, so we need to clear the previous preselectResult
                    preselectResult = null;
                }
            }

            // can this result be cached? Don't cache if the result depends on local variables.
            final boolean canCache = ( contextSequence != null ) && contextSequence.isCacheable() && !Dependency.dependsOn( getLeft(), Dependency.CONTEXT_ITEM ) && !Dependency.dependsOn( getRight(), Dependency.CONTEXT_ITEM ) && !Dependency.dependsOnVar( getLeft() ) && !Dependency.dependsOnVar( getRight() );

            if( canCache ) {
                cached = new CachedResult( contextSequence, contextItem, result );
            }

        }

        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().end( this, "", result );
        }

        actualReturnType = result.getItemType();

        return( result );
    }


    /**
     * Generic, slow implementation. Applied if none of the possible optimizations can be used.
     *
     * @param   contextSequence
     * @param   contextItem
     *
     * @return  The Sequence resulting from the comparison
     *
     * @throws  XPathException
     */
    protected Sequence genericCompare( Sequence contextSequence, Item contextItem ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "genericCompare");
        }
        final Sequence ls = getLeft().eval( contextSequence, contextItem );
        return( genericCompare( ls, contextSequence, contextItem ) );
    }


    protected Sequence genericCompare( Sequence ls, Sequence contextSequence, Item contextItem ) throws XPathException
    {
        final long           start    = System.currentTimeMillis();
        final Sequence rs       = getRight().eval(contextSequence, contextItem);
        final Collator collator = getCollator( contextSequence );
        Sequence       result   = BooleanValue.FALSE;

        if( ls.isEmpty() && rs.isEmpty() ) {
            result = BooleanValue.valueOf( compareAtomic( collator, AtomicValue.EMPTY_VALUE, AtomicValue.EMPTY_VALUE ) );
        } else if( ls.isEmpty() && !rs.isEmpty() ) {

            for( final SequenceIterator i2 = Atomize.atomize(rs).iterate(); i2.hasNext(); ) {

                if( compareAtomic( collator, AtomicValue.EMPTY_VALUE, i2.nextItem().atomize() ) ) {
                    result = BooleanValue.TRUE;
                    break;
                }
            }
        } else if( !ls.isEmpty() && rs.isEmpty() ) {

            for( final SequenceIterator i1 = Atomize.atomize(ls).iterate(); i1.hasNext(); ) {
                final AtomicValue lv = i1.nextItem().atomize();

                if( compareAtomic( collator, lv, AtomicValue.EMPTY_VALUE ) ) {
                    result = BooleanValue.TRUE;
                    break;
                }
            }
        } else if( ls.hasOne() && rs.hasOne() && ls.itemAt(0).getType() != Type.ARRAY && rs.itemAt(0).getType() != Type.ARRAY) {
            result = BooleanValue.valueOf( compareAtomic( collator, ls.itemAt( 0 ).atomize(), rs.itemAt( 0 ).atomize() ) );
        } else {

            for( final SequenceIterator i1 = Atomize.atomize(ls).iterate(); i1.hasNext(); ) {
                final AtomicValue lv = i1.nextItem().atomize();

                if( rs.isEmpty() ) {

                    if( compareAtomic( collator, lv, AtomicValue.EMPTY_VALUE ) ) {
                        result = BooleanValue.TRUE;
                        break;
                    }
                } else if( rs.hasOne() && rs.itemAt(0).getType() != Type.ARRAY) {

                    if( compareAtomic( collator, lv, rs.itemAt( 0 ).atomize() ) ) {

                        //return early if we are successful, continue otherwise
                        result = BooleanValue.TRUE;
                        break;
                    }
                } else {

                    for( final SequenceIterator i2 = Atomize.atomize(rs).iterate(); i2.hasNext(); ) {

                        if( compareAtomic( collator, lv, i2.nextItem().atomize() ) ) {
                            result = BooleanValue.TRUE;
                            break;
                        }
                    }
                }
            }
        }

        return result;
    }


    /**
     * Optimized implementation, which can be applied if the left operand returns a node set. In this case, the left expression is executed first. All
     * matching context nodes are then passed to the right expression.
     *
     * @param   nodes            DOCUMENT ME!
     * @param   contextSequence  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  XPathException  DOCUMENT ME!
     */
    protected Sequence nodeSetCompare( final NodeSet nodes, final Sequence contextSequence ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare" );
        }

        if( LOG.isTraceEnabled() ) {
            LOG.trace( "No index: fall back to nodeSetCompare" );
        }
        final long           start    = System.currentTimeMillis();
        final NodeSet        result   = new NewArrayNodeSet();
        final Collator collator = getCollator(contextSequence);

        if( ( contextSequence != null ) && !contextSequence.isEmpty() && !contextSequence.getDocumentSet().contains( nodes.getDocumentSet() ) ) {

            for( final NodeProxy item : nodes ) {
                ContextItem context = item.getContext();

                if( context == null ) {
                    throw( new XPathException( this, "Internal error: context node missing" ) );
                }
                final AtomicValue lv = item.atomize();

                do {
                    final Sequence rs = getRight().eval( context.getNode().toSequence() );

                    for( final SequenceIterator i2 = Atomize.atomize(rs).iterate(); i2.hasNext(); ) {
                        final AtomicValue rv = i2.nextItem().atomize();

                        if( compareAtomic( collator, lv, rv ) ) {
                            result.add( item );
                        }
                    }
                } while( ( context = context.getNextDirect() ) != null );
            }
        } else {

            for( final NodeProxy item : nodes ) {
                final AtomicValue lv = item.atomize();
                final Sequence    rs = getRight().eval( contextSequence );

                for( final SequenceIterator i2 = Atomize.atomize(rs).iterate(); i2.hasNext(); ) {
                    final AtomicValue rv = i2.nextItem().atomize();

                    if( compareAtomic( collator, lv, rv ) ) {
                        result.add( item );
                    }
                }
            }
        }

        return result;
    }


    /**
     * Optimized implementation - shorter path if there is no work to do on left or right,
     * otherwise, fall back to {@link #nodeSetCompare(NodeSet, Sequence)}.
     *
     * @param contextSequence
     *
     * @throws XPathException
     */
    protected Sequence quickNodeSetCompare(final Sequence contextSequence) throws XPathException {
        /* TODO think about optimising fallback to NodeSetCompare() in the for loop!!!
         * At the moment when we fallback to NodeSetCompare() we are in effect throwing away any nodes
         * we have already processed in quickNodeSetCompare() and reprocessing all the nodes in NodeSetCompare().
         * Instead - Could we create a NodeCompare() (based on NodeSetCompare() code) to only compare a single node and then union the result?
         * - deliriumsky
         */

        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "quickNodeSetCompare" );
        }

        final long     start   = System.currentTimeMillis();

        //get the NodeSet on the left
        final Sequence leftSeq = getLeft().eval( contextSequence );

        if( !leftSeq.isPersistentSet() ) {
            return( genericCompare( leftSeq, contextSequence, null ) );
        }

        final NodeSet nodes = leftSeq.isEmpty() ? NodeSet.EMPTY_SET : ( NodeSet )leftSeq;

        //nothing on the left, so nothing to do
        if( !( nodes instanceof VirtualNodeSet ) && nodes.isEmpty() ) {

            //Well, we might discuss this one ;-)
            hasUsedIndex = true;
            return( Sequence.EMPTY_SEQUENCE );
        }

        //get the Sequence on the right
        final Sequence rightSeq = getRight().eval( contextSequence );

        //nothing on the right, so nothing to do
        if( rightSeq.isEmpty() ) {

            //Well, we might discuss this one ;-)
            hasUsedIndex = true;
            return( Sequence.EMPTY_SEQUENCE );
        }

        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "falling back to nodeSetCompare (no index available)" );
        }

        return( nodeSetCompare( nodes, contextSequence ) );
    }


    /**
     * Cast the atomic operands into a comparable type and compare them.
     *
     * @param   collator  DOCUMENT ME!
     * @param   lv        DOCUMENT ME!
     * @param   rv        DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  XPathException  DOCUMENT ME!
     */
    private boolean compareAtomic( Collator collator, AtomicValue lv, AtomicValue rv ) throws XPathException
    {
        try {
            final int ltype = lv.getType();
            final int rtype = rv.getType();

            if( ltype == Type.UNTYPED_ATOMIC ) {

                //If one of the atomic values is an instance of xdt:untypedAtomic
                //and the other is an instance of a numeric type,
                //then the xdt:untypedAtomic value is cast to the type xs:double.
                if( Type.subTypeOf( rtype, Type.NUMBER ) ) {

                    //if(isEmptyString(lv))
                    //    return false;
                    lv = lv.convertTo( Type.DOUBLE );

                    //If one of the atomic values is an instance of xdt:untypedAtomic
                    //and the other is an instance of xdt:untypedAtomic or xs:string,
                    //then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
                } else if( ( rtype == Type.UNTYPED_ATOMIC ) || ( rtype == Type.STRING ) ) {
                    lv = lv.convertTo( Type.STRING );
                    //if (rtype == Type.UNTYPED_ATOMIC)
                    //rv = rv.convertTo(Type.STRING);
                    //If one of the atomic values is an instance of xdt:untypedAtomic
                    //and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type,
                    //then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
                } else {
                    lv = lv.convertTo( rtype );
                }
            }

            if( rtype == Type.UNTYPED_ATOMIC ) {

                //If one of the atomic values is an instance of xdt:untypedAtomic
                //and the other is an instance of a numeric type,
                //then the xdt:untypedAtomic value is cast to the type xs:double.
                if( Type.subTypeOf( ltype, Type.NUMBER ) ) {

                    //if(isEmptyString(lv))
                    //    return false;
                    rv = rv.convertTo( Type.DOUBLE );

                    //If one of the atomic values is an instance of xdt:untypedAtomic
                    //and the other is an instance of xdt:untypedAtomic or xs:string,
                    //then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
                } else if( ( ltype == Type.UNTYPED_ATOMIC ) || ( ltype == Type.STRING ) ) {
                    rv = rv.convertTo( Type.STRING );
                    //if (ltype == Type.UNTYPED_ATOMIC)
                    //  lv = lv.convertTo(Type.STRING);
                    //If one of the atomic values is an instance of xdt:untypedAtomic
                    //and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type,
                    //then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
                } else {
                    rv = rv.convertTo( ltype );
                }
            }

            /*
            if (backwardsCompatible) {
                if (!"".equals(lv.getStringValue()) && !"".equals(rv.getStringValue())) {
                    // in XPath 1.0 compatible mode, if one of the operands is a number, cast
                    // both operands to xs:double
                    if (Type.subTypeOf(ltype, Type.NUMBER)
                        || Type.subTypeOf(rtype, Type.NUMBER)) {
                            lv = lv.convertTo(Type.DOUBLE);
                            rv = rv.convertTo(Type.DOUBLE);
                    }
                }
            }
            */
            // if truncation is set, we always do a string comparison
            if( truncation != StringTruncationOperator.NONE ) {

                //TODO : log this ?
                lv = lv.convertTo( Type.STRING );
            }

//              System.out.println(
//                  lv.getStringValue() + Constants.OPS[relation] + rv.getStringValue());
            switch( truncation ) {

                case RIGHT: {
                    return( lv.startsWith( collator, rv ) );
                }

                case LEFT: {
                    return( lv.endsWith( collator, rv ) );
                }

                case BOTH: {
                    return( lv.contains( collator, rv ) );
                }

                default: {
                    return( lv.compareTo( collator, relation, rv ) );
                }
            }
        }
        catch( final XPathException e ) {
            e.setLocation( e.getLine(), e.getColumn() );
            throw( e );
        }
    }

    @Override
    public boolean hasUsedIndex()
    {
        return( hasUsedIndex );
    }


    @Override
    public void dump( ExpressionDumper dumper )
    {
        if( truncation == StringTruncationOperator.BOTH ) {
            dumper.display( "contains" ).display( '(' );
            getLeft().dump( dumper );
            dumper.display( ", " );
            getRight().dump( dumper );
            dumper.display( ")" );
        } else {
            getLeft().dump( dumper );
            dumper.display( ' ' ).display( relation.generalComparisonSymbol ).display( ' ' );
            getRight().dump( dumper );
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder result = new StringBuilder();

        if( truncation == StringTruncationOperator.BOTH ) {
            result.append( "contains" ).append( '(' );
            result.append( getLeft().toString() );
            result.append( ", " );
            result.append( getRight().toString() );
            result.append( ")" );
        } else {
            result.append( getLeft().toString() );
            result.append( ' ' ).append( relation.generalComparisonSymbol ).append( ' ' );
            result.append( getRight().toString() );
        }
        return( result.toString() );
    }


    protected void switchOperands()
    {
        context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Switching operands" );

        //Invert relation
        switch(relation) {

            case GT:
                relation = Comparison.LT;
                break;

            case LT:
                relation = Comparison.GT;
                break;

            case LTEQ:
                relation = Comparison.GTEQ;
                break;

            case GTEQ:
                relation = Comparison.LTEQ;
                break;
                //What about Comparison.EQand Comparison.NEQ? Well, it seems to never be called
        }
        final Expression right = getRight();
        setRight(getLeft());
        setLeft(right);
    }

    protected Collator getCollator( Sequence contextSequence ) throws XPathException
    {
        if( collationArg == null ) {
            return( context.getDefaultCollator() );
        }

        String collationURI;

        if( collationArg instanceof Expression ) {
            collationURI = ( ( Expression )collationArg ).eval( contextSequence ).getStringValue();
        } else if( collationArg instanceof StringValue ) {
            collationURI = ( ( StringValue )collationArg ).getStringValue();
        } else {
            return( context.getDefaultCollator() );
        }

        return( context.getCollator( collationURI ) );
    }


    public void setCollation( Object collationArg )
    {
        this.collationArg = collationArg;
    }

    @Override
    public void resetState( boolean postOptimization )
    {
        super.resetState( postOptimization );
        getLeft().resetState( postOptimization );
        getRight().resetState( postOptimization );

        if( !postOptimization ) {
            cached          = null;
            preselectResult = null;
            hasUsedIndex    = false;
        }
    }

    @Override
    public void accept( ExpressionVisitor visitor )
    {
        visitor.visitGeneralComparison( this );
    }
}
