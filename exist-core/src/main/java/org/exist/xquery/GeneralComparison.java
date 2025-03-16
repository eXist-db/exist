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

import com.ibm.icu.text.Collator;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.IndexSpec;
import org.exist.storage.Indexable;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.pragmas.Optimize;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.Iterator;
import java.util.List;


/**
 * A general XQuery/XPath2 comparison expression.
 *
 * @author  wolf
 * @author  andrzej@chaeron.com
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GeneralComparison extends BinaryOp implements Optimizable, IndexUseReporter
{
    /** The type of operator used for the comparison, i.e. =, !=, &lt;, &gt; ... One of the constants declared in class {@link Constants}. */
    protected Comparison          relation              = Comparison.EQ;

    /**
     * Truncation flags: when comparing with a string value, the search string may be truncated with a single * wildcard. See the constants declared
     * in class {@link Constants}.
     *
     * The standard functions starts-with, ends-with and contains are transformed into a general comparison with wildcard. Hence the need to
     * consider wildcards here.
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

    private IndexFlags     idxflags         = new IndexFlags();

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
        super( context );
        boolean didLeftSimplification  = false;
        boolean didRightSimplification = false;
        this.relation   = relation;
        this.truncation = truncation;

        if( ( left instanceof PathExpr ) && ( ( ( PathExpr )left ).getLength() == 1 ) ) {
            left                  = ( ( PathExpr )left ).getExpression( 0 );
            didLeftSimplification = true;
        }
        add( left );

        if( ( right instanceof PathExpr ) && ( ( ( PathExpr )right ).getLength() == 1 ) ) {
            right                  = ( ( PathExpr )right ).getExpression( 0 );
            didRightSimplification = true;
        }
        add( right );

        //TODO : should we also use simplify() here ? -pb
        if( didLeftSimplification ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Marked left argument as a child expression" );
        }

        if( didRightSimplification ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Marked right argument as a child expression" );
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BinaryOp#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
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
            LocationStep lastStep  = steps.getLast();

            if( firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();

                if( ( outerExpr != null ) && (outerExpr instanceof LocationStep outerStep) ) {
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
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        if (contextQName != null && Optimize.getQNameIndexType(context, contextSequence, contextQName) != Type.ITEM) {
            return contextSequence;
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    public boolean optimizeOnSelf()
    {
        return( optimizeSelf );
    }


    public boolean optimizeOnChild()
    {
        return( optimizeChild );
    }


    public int getOptimizeAxis()
    {
        return( axis );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.BinaryOp#returnsType()
     */
    public int returnsType()
    {
        if( inPredicate && ( !Dependency.dependsOn( this, Dependency.CONTEXT_ITEM ) ) ) {
            return( getLeft().returnsType() );
        }

        // In all other cases, we return boolean
        return( Type.BOOLEAN );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
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

    public NodeSet preSelect( Sequence contextSequence, boolean useContext ) throws XPathException
    {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        final long start     = System.currentTimeMillis();
        final int  indexType = Optimize.getQNameIndexType( context, contextSequence, contextQName );

        if( LOG.isTraceEnabled() ) {
            LOG.trace("Using QName index on type {}", Type.getTypeName(indexType));
        }

        final Sequence rightSeq = getRight().eval(contextSequence, null);
        
        // if the right hand sequence has more than one item, we need to merge them
        // into preselectResult
        if (rightSeq.getItemCount() > 1) {preselectResult = new NewArrayNodeSet();}
        
        // Iterate through each item in the right-hand sequence
        for( final SequenceIterator itRightSeq = Atomize.atomize(rightSeq).iterate(); itRightSeq.hasNext(); ) {

            //Get the index key
            Item key = itRightSeq.nextItem();

            //if key has truncation, convert it to string
            if( truncation != StringTruncationOperator.NONE ) {

                if( !Type.subTypeOf( key.getType(), Type.STRING ) ) {
                    LOG.info("Truncated key. Converted from {} to xs:string", Type.getTypeName(key.getType()));

                    //truncation is only possible on strings
                    key = key.convertTo( Type.STRING );
                }
            }
            //else if key is not the same type as the index
            //TODO : use Type.isSubType() ??? -pb
            else if( key.getType() != indexType ) {

                //try to convert the key to the index type
                try {
                    key = key.convertTo( indexType );
                }
                catch( final XPathException xpe ) {

                    if( LOG.isTraceEnabled() ) {
                        LOG.trace("Cannot convert key: {} to required index type: {}", Type.getTypeName(key.getType()), Type.getTypeName(indexType));
                    }

                    throw( new XPathException( this, "Cannot convert key to required index type" ) );
                }
            }

            // If key implements org.exist.storage.Indexable, we can use the index
            if( key instanceof Indexable ) {

                if( LOG.isTraceEnabled() ) {
                    LOG.trace("Using QName range index for key: {}", key.getStringValue());
                }

                NodeSet  temp;
                final NodeSet  contextSet = useContext ? contextSequence.toNodeSet() : null;
                final Collator collator   = ( ( collationArg != null ) ? getCollator( contextSequence ) : null );

                if( truncation == StringTruncationOperator.NONE ) {
                    temp         = context.getBroker().getValueIndex().find(context.getWatchDog(), relation, contextSequence.getDocumentSet(), contextSet, NodeSet.DESCENDANT, contextQName, ( Indexable )key);
                    hasUsedIndex = true;
                } else {

                    try {
                        final String matchString = key.getStringValue();
                        final int    matchType   = getMatchType( truncation );

                        temp         = context.getBroker().getValueIndex().match(context.getWatchDog(), contextSequence.getDocumentSet(), contextSet, NodeSet.DESCENDANT, matchString, contextQName, matchType, collator, truncation );

                        hasUsedIndex = true;
                    }
                    catch( final EXistException e ) {
                        throw( new XPathException( this, "Error during index lookup: " + e.getMessage(), e ) );
                    }
                }

                // if the right-hand sequence has more than one item,
                // merge the result of the iteration into preselectResult,
                // else replace it.
                if( preselectResult == null ) {
                    preselectResult = temp;
                } else {
                    preselectResult.addAll(temp);
                }
            }
        }

        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, PerformanceStats.RANGE_IDX_TYPE, this, PerformanceStats.IndexOptimizationLevel.OPTIMIZED, System.currentTimeMillis() - start );
        }

        return( ( preselectResult == null ) ? NodeSet.EMPTY_SET : preselectResult );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().start( this );
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
                            final NodeSet nodes = ( NodeSet )getLeft().eval(contextSequence, null);
                            result = nodeSetCompare( nodes, contextSequence );
                        }
                    } else {
                        result = genericCompare( contextSequence, contextItem );
                    }
                } else {
                    contextStep.setPreloadedData( preselectResult.getDocumentSet(), preselectResult );
                    result = getLeft().eval(contextSequence, null).toNodeSet();
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
     * @param   contextSequence the context sequence
     * @param   contextItem optional context item
     *
     * @return  The Sequence resulting from the comparison
     *
     * @throws  XPathException in case of dynamic error
     */
    protected Sequence genericCompare( Sequence contextSequence, Item contextItem ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "genericCompare" );
        }
        final Sequence ls = getLeft().eval( contextSequence, contextItem );
        return( genericCompare( ls, contextSequence, contextItem ) );
    }


    protected Sequence genericCompare( Sequence ls, Sequence contextSequence, Item contextItem ) throws XPathException
    {
        final long           start    = System.currentTimeMillis();
        final Sequence rs       = getRight().eval( contextSequence, contextItem );
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
        } else if( ls.hasOne() && rs.hasOne() && ls.itemAt(0).getType() != Type.ARRAY_ITEM && rs.itemAt(0).getType() != Type.ARRAY_ITEM) {
            result = BooleanValue.valueOf( compareAtomic( collator, ls.itemAt( 0 ).atomize(), rs.itemAt( 0 ).atomize() ) );
        } else {

            for( final SequenceIterator i1 = Atomize.atomize(ls).iterate(); i1.hasNext(); ) {
                final AtomicValue lv = i1.nextItem().atomize();

                if( rs.isEmpty() ) {

                    if( compareAtomic( collator, lv, AtomicValue.EMPTY_VALUE ) ) {
                        result = BooleanValue.TRUE;
                        break;
                    }
                } else if( rs.hasOne() && rs.itemAt(0).getType() != Type.ARRAY_ITEM) {

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

        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, PerformanceStats.RANGE_IDX_TYPE, this, PerformanceStats.IndexOptimizationLevel.NONE, System.currentTimeMillis() - start );
        }
        return( result );
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
    protected Sequence nodeSetCompare( NodeSet nodes, Sequence contextSequence ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare" );
        }

        if( LOG.isTraceEnabled() ) {
            LOG.trace( "No index: fall back to nodeSetCompare" );
        }
        final long           start    = System.currentTimeMillis();
        final NodeSet        result   = new NewArrayNodeSet();
        final Collator collator = getCollator( contextSequence );

        if( ( contextSequence != null ) && !contextSequence.isEmpty() && !contextSequence.getDocumentSet().contains( nodes.getDocumentSet() ) ) {

            for( final NodeProxy item : nodes ) {
                ContextItem context = item.getContext();

                if( context == null ) {
                    throw( new XPathException( this, "Internal error: context node missing" ) );
                }
                final AtomicValue lv = item.atomize();

                do {
                    final Sequence rs = getRight().eval(context.getNode().toSequence(), null);

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
                final Sequence    rs = getRight().eval(contextSequence, null);

                for( final SequenceIterator i2 = Atomize.atomize(rs).iterate(); i2.hasNext(); ) {
                    final AtomicValue rv = i2.nextItem().atomize();

                    if( compareAtomic( collator, lv, rv ) ) {
                        result.add( item );
                    }
                }
            }
        }

        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, PerformanceStats.RANGE_IDX_TYPE, this, PerformanceStats.IndexOptimizationLevel.NONE, System.currentTimeMillis() - start );
        }
        return( result );
    }


    /**
     * Optimized implementation: first checks if a range index is defined on the nodes in the left argument.
     * Otherwise, fall back to {@link #nodeSetCompare(NodeSet, Sequence)}.
     *
     * @param   contextSequence  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  XPathException  DOCUMENT ME!
     */
    protected Sequence quickNodeSetCompare( Sequence contextSequence ) throws XPathException
    {
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
        final Sequence leftSeq = getLeft().eval(contextSequence, null);

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
        final Sequence rightSeq = getRight().eval(contextSequence, null);

        //nothing on the right, so nothing to do
        if( rightSeq.isEmpty() ) {

            //Well, we might discuss this one ;-)
            hasUsedIndex = true;
            return( Sequence.EMPTY_SEQUENCE );
        }

        //get the type of a possible index
        final int indexType = nodes.getIndexType();

        //See if we have a range index defined on the nodes in this sequence
        //remember that Type.ITEM means... no index ;-)
        if( indexType != Type.ITEM ) {

            if( LOG.isTraceEnabled() ) {
                LOG.trace("found an index of type: {}", Type.getTypeName(indexType));
            }

            boolean indexScan = false;
            boolean indexMixed = false;
            QName myContextQName = contextQName;
            if( contextSequence != null ) {
                final IndexFlags iflags     = checkForQNameIndex( idxflags, context, contextSequence, myContextQName );
                boolean    indexFound = false;

                if( !iflags.indexOnQName ) {
                    // if myContextQName != null and no index is defined on
                    // myContextQName, we don't need to scan other QName indexes
                    // and can just use the generic range index
                    indexFound   = myContextQName != null;

                    if (iflags.partialIndexOnQName) {
                        indexMixed = true;
                    } else {
                        // set myContextQName to null so the index lookup below is not
                        // restricted to that QName
                        myContextQName = null;
                    }
                }

                if( !indexFound && ( myContextQName == null ) ) {

                    // if there are some indexes defined on a qname,
                    // we need to check them all
                    if( iflags.hasIndexOnQNames ) {
                        indexScan = true;
                    }
                    // else use range index defined on path by default
                }
            } else {
                return( nodeSetCompare( nodes, contextSequence ) );
            }

            //Get the documents from the node set
            final DocumentSet docs   = nodes.getDocumentSet();

            //Holds the result
            NodeSet           result = null;

            //Iterate through the right hand sequence
            for( final SequenceIterator itRightSeq = Atomize.atomize(rightSeq).iterate(); itRightSeq.hasNext(); ) {

                //Get the index key
                Item key = itRightSeq.nextItem();

                //if key has truncation, convert it to string
                if( truncation != StringTruncationOperator.NONE ) {

                    if( !Type.subTypeOf( key.getType(), Type.STRING ) ) {
                        LOG.info("Truncated key. Converted from {} to xs:string", Type.getTypeName(key.getType()));

                        //truncation is only possible on strings
                        key = key.convertTo( Type.STRING );
                    }
                }
                //else if key is not the same type as the index
                //TODO : use Type.isSubType() ??? -pb
                else if( key.getType() != indexType ) {

                    //try to convert the key to the index type
                    try {
                        key = key.convertTo( indexType );
                    }
                    catch( final XPathException xpe ) {
                        //TODO : rethrow the exception ? -pb

                        //Could not convert the key to a suitable type for the index, fallback to nodeSetCompare()
                        if( context.getProfiler().isEnabled() ) {
                            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (" + xpe.getMessage() + ")" );
                        }

                        if( LOG.isTraceEnabled() ) {
                            LOG.trace("Cannot convert key: {} to required index type: {}", Type.getTypeName(key.getType()), Type.getTypeName(indexType));
                        }

                        return( nodeSetCompare( nodes, contextSequence ) );
                    }
                }

                // If key implements org.exist.storage.Indexable, we can use the index
                if( key instanceof Indexable ) {

                    if( LOG.isTraceEnabled() ) {
                        LOG.trace("Checking if range index can be used for key: {}", key.getStringValue());
                    }

                    final Collator collator = ( ( collationArg != null ) ? getCollator( contextSequence ) : null );

                    if( Type.subTypeOf( key.getType(), indexType ) ) {

                        if( truncation == StringTruncationOperator.NONE ) {

                            if( LOG.isTraceEnabled() ) {
                                LOG.trace("Using range index for key: {}", key.getStringValue());
                            }

                            //key without truncation, find key
                            context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() + "' to find key '" + Type.getTypeName( key.getType() ) + "(" + key.getStringValue() + ")'" );

                            NodeSet ns;

                            if( indexScan ) {
                                ns = context.getBroker().getValueIndex().findAll( context.getWatchDog(), relation, docs, nodes, NodeSet.ANCESTOR, ( Indexable )key);
                            } else {
                                ns = context.getBroker().getValueIndex().find( context.getWatchDog(), relation, docs, nodes, NodeSet.ANCESTOR, myContextQName,
                                        ( Indexable )key, indexMixed );
                            }
                            hasUsedIndex = true;

                            if( result == null ) {
                                result = ns;
                            } else {
                                result = result.union( ns );
                            }

                        } else {

                            //key with truncation, match key
                            if( LOG.isTraceEnabled() ) {
                                context.getProfiler().message( this, Profiler.OPTIMIZATIONS, "OPTIMIZATION", "Using value index '" + context.getBroker().getValueIndex().toString() + "' to match key '" + Type.getTypeName( key.getType() ) + "(" + key.getStringValue() + ")'" );
                            }

                            if( LOG.isTraceEnabled() ) {
                                LOG.trace("Using range index for key: {}", key.getStringValue());
                            }

                            try {
                                NodeSet ns;

                                final String  matchString = key.getStringValue();
                                final int     matchType   = getMatchType( truncation );

                                if( indexScan ) {
                                    ns = context.getBroker().getValueIndex().matchAll( context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, matchString, matchType, 0, true, collator, truncation );
                                } else {
                                    ns = context.getBroker().getValueIndex().match( context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, matchString, myContextQName, matchType, collator, truncation );
                                }

                                hasUsedIndex = true;

                                if( result == null ) {
                                    result = ns;
                                } else {
                                    result = result.union( ns );
                                }

                            }
                            catch( final EXistException e ) {
                                throw( new XPathException( this, e ) );
                            }
                        }
                    } else {

                        //our key does is not of the correct type
                        if( context.getProfiler().isEnabled() ) {
                            context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is of type: " + Type.getTypeName( key.getType() ) + ") whereas index is of type '" + Type.getTypeName( indexType ) + "'" );
                        }

                        if( LOG.isTraceEnabled() ) {
                            LOG.trace("Cannot use range index: key is of type: {}) whereas index is of type '{}", Type.getTypeName(key.getType()), Type.getTypeName(indexType));
                        }

                        return( nodeSetCompare( nodes, contextSequence ) );
                    }
                } else {

                    //our key does not implement org.exist.storage.Indexable
                    if( context.getProfiler().isEnabled() ) {
                        context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "Falling back to nodeSetCompare (key is not an indexable type: " + key.getClass().getName() );
                    }

                    if( LOG.isTraceEnabled() ) {
                        LOG.trace("Cannot use key which is of type '{}", key.getClass().getName());
                    }

                    return( nodeSetCompare( nodes, contextSequence ) );

                }
            }

            if( context.getProfiler().traceFunctions() ) {
                context.getProfiler().traceIndexUsage( context, PerformanceStats.RANGE_IDX_TYPE, this, PerformanceStats.IndexOptimizationLevel.BASIC, System.currentTimeMillis() - start );
            }
            return( result );
        } else {

            if( LOG.isTraceEnabled() ) {
                LOG.trace("No suitable index found for key: {}", rightSeq.getStringValue());
            }

            //no range index defined on the nodes in this sequence, so fallback to nodeSetCompare
            if( context.getProfiler().isEnabled() ) {
                context.getProfiler().message( this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION FALLBACK", "falling back to nodeSetCompare (no index available)" );
            }

            return( nodeSetCompare( nodes, contextSequence ) );
        }
    }


    private int getMatchType( StringTruncationOperator truncation ) throws XPathException
    {
        int matchType;

        // Figure out what type of matching we need to do.

        switch( truncation ) {

            case RIGHT: {
                matchType = DBBroker.MATCH_STARTSWITH;
                break;
            }

            case LEFT: {
                matchType = DBBroker.MATCH_ENDSWITH;
                break;
            }

            case BOTH: {
                matchType = DBBroker.MATCH_CONTAINS;
                break;
            }

            case EQUALS: {
                matchType = DBBroker.MATCH_EXACT;
                break;
            }

            default: {

                // We should never get here!
                LOG.error("Invalid truncation type: {}", truncation);
                throw( new XPathException( this, "Invalid truncation type: " + truncation ) );
            }
        }

        return( matchType );
    }


    private CharSequence getRegexp( String expr )
    {
        return switch (truncation) {
            case LEFT -> (new StringBuilder().append(expr).append('$'));
            case RIGHT -> (new StringBuilder().append('^').append(expr));
            default -> (expr);
        };
    }

    private AtomicValue convertForGeneralComparison(final AtomicValue value, final int thisType, final int otherType)
            throws XPathException {
        if (thisType == Type.UNTYPED_ATOMIC) {

            try {

                /*
                    If both atomic values are instances of xs:untypedAtomic,
                    then the values are cast to the type xs:string.
                 */
                if (otherType == Type.UNTYPED_ATOMIC) {
                    return value.convertTo(Type.STRING);
                }

                // it is cast to a type depending on the other value's dynamic type T

                /*
                    i. If T is a numeric type or is derived from a numeric type,
                    then V is cast to xs:double.
                 */
                if (Type.subTypeOfUnion(otherType, Type.NUMERIC)) {
                    return value.convertTo(Type.DOUBLE);
                }

                /*
                    ii. If T is xs:dayTimeDuration or is derived from xs:dayTimeDuration,
                        then V is cast to xs:dayTimeDuration.
                 */
                if (Type.subTypeOf(otherType, Type.DAY_TIME_DURATION)) {
                    return value.convertTo(Type.DAY_TIME_DURATION);
                }

                /*
                    iii. If T is xs:yearMonthDuration or is derived from xs:yearMonthDuration,
                         then V is cast to xs:yearMonthDuration.
                 */
                if (Type.subTypeOf(otherType, Type.YEAR_MONTH_DURATION)) {
                    return value.convertTo(Type.YEAR_MONTH_DURATION);
                }

                /*
                    iv. In all other cases, V is cast to the primitive base type of T.
                 */
                return value.convertTo(otherType);

            } catch (XPathException e) {
                if (e.getErrorCode() != ErrorCodes.FORG0001) {
                    e = new XPathException(this, ErrorCodes.FORG0001, e.getMessage(), e);
                }
                throw e;
            }
        }

        return value;
    }

    private AtomicValue convertForValueComparison(final AtomicValue value, final int thisType, final int otherType)
            throws XPathException {
        /*
            if the two operands are instances of different primitive types then:
         */
        if (Type.primitiveTypeOf(thisType) != Type.primitiveTypeOf(otherType)) {

            /*
                a. If each operand is an instance of one of the types xs:string or xs:anyURI,
                   then both operands are cast to type xs:string.
             */
            if ((Type.subTypeOf(thisType, Type.STRING) || thisType == Type.ANY_URI)
                    && (Type.subTypeOf(otherType, Type.STRING) || otherType ==Type.ANY_URI)) {
                return value.convertTo(Type.STRING);
            }

            /*
                b. If each operand is an instance of one of the types xs:decimal or xs:float,
                then both operands are cast to type xs:float.
             */
            if ((Type.subTypeOf(thisType, Type.DECIMAL) || thisType == Type.FLOAT)
                    && (Type.subTypeOf(otherType, Type.DECIMAL) || otherType == Type.FLOAT)) {
                return value.convertTo(Type.FLOAT);
            }

            /*
             *  c. If each operand is an instance of one of the types xs:decimal, xs:float, or xs:double,
             *     then both operands are cast to type xs:double.
             */
            if ((Type.subTypeOf(thisType, Type.DECIMAL) || thisType == Type.FLOAT || thisType == Type.DOUBLE)
                    && (Type.subTypeOf(otherType, Type.DECIMAL) || otherType == Type.FLOAT || otherType == Type.DOUBLE)) {
                return value.convertTo(Type.DOUBLE);
            }

            /*
             *  d. Otherwise, a type error is raised [err:XPTY0004].
             */
            throw new XPathException(this, ErrorCodes.XPTY0004, "Incompatible primitive types");
        }

        return value;
    }

    /**
     * Cast the atomic operands into a comparable type and compare them.
     *
     * @param collator the collator to use for comparisons
     * @param lv left-hand-side value of comparison
     * @param rv right-hand-side value of comparison
     *
     * @return true if the comparison holds, false otherwise
     *
     * @throws XPathException if an error occurs during the comparison
     */
    private boolean compareAtomic(final Collator collator, AtomicValue lv, AtomicValue rv) throws XPathException {
        // get types locally as convertForCompareAtomic may change the types of the AtomicValue itself
        int ltype = lv.getType();
        int rtype = rv.getType();

        lv = convertForGeneralComparison(lv, ltype, rtype);
        rv = convertForGeneralComparison(rv, rtype, ltype);

        // if truncation is set, we always do a string comparison
        if (truncation != StringTruncationOperator.NONE) {
            lv = lv.convertTo(Type.STRING);
        }

        switch (truncation) {
            case RIGHT:
                return lv.startsWith(collator, rv);

            case LEFT:
                return lv.endsWith(collator, rv);

            case BOTH:
                return lv.contains(collator, rv);
        }

        /*
         * If an atomized operand is an empty sequence, the result of the value comparison is an empty sequence
         * and the implementation need not evaluate the other operand or apply the operator.
         */
        if (lv.isEmpty() || rv.isEmpty()) {
            return false;
        }

        // get types locally as convertForValueComparison may change the types of the AtomicValue itself
        ltype = lv.getType();
        rtype = rv.getType();

        lv = convertForValueComparison(lv, ltype, rtype);
        rv = convertForValueComparison(rv, rtype, ltype);

        return lv.compareTo(collator, relation, rv);
    }


    /**
     * DOCUMENT ME!
     *
     * @param   lv
     *
     * @return  Whether or not <code>lv</code> is an empty string
     *
     * @throws  XPathException
     */
    @SuppressWarnings( "unused" )
    private static boolean isEmptyString( AtomicValue lv ) throws XPathException
    {
        if( Type.subTypeOf( lv.getType(), Type.STRING ) || ( lv.getType() == Type.ANY_ATOMIC_TYPE) ) {

            if(lv.getStringValue().isEmpty()) {
                return( true );
            }
        }
        return( false );
    }


    public boolean hasUsedIndex()
    {
        return( hasUsedIndex );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
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


    /**
     * Possibly switch operands to simplify execution.
     */
    protected void simplifyOperands()
    {
        //Prefer nodes at the left hand
        if( ( !Type.subTypeOf( getLeft().returnsType(), Type.NODE ) ) && Type.subTypeOf( getRight().returnsType(), Type.NODE ) ) {
            switchOperands();
        }
        //Prefer fewer items at the left hand
        else if (Cardinality._MANY.isSuperCardinalityOrEqualOf(getLeft().getCardinality())
                && !Cardinality._MANY.isSuperCardinalityOrEqualOf(getRight().getCardinality())) {
            switchOperands();
        }
    }


    protected Collator getCollator( Sequence contextSequence ) throws XPathException
    {
        if( collationArg == null ) {
            return( context.getDefaultCollator() );
        }

        String collationURI;

        if( collationArg instanceof Expression ) {
            collationURI = ( ( Expression )collationArg ).eval(contextSequence, null).getStringValue();
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


    public final static IndexFlags checkForQNameIndex( IndexFlags idxflags, XQueryContext context, Sequence contextSequence, QName contextQName )
    {
        idxflags.reset( contextQName != null );

        for( final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();

            if( collection.getURI().equalsInternal( XmldbURI.SYSTEM_COLLECTION_URI ) ) {
                continue;
            }
            final IndexSpec idxcfg = collection.getIndexConfiguration( context.getBroker() );

            if( idxflags.indexOnQName && ( idxcfg.getIndexByQName( contextQName ) == null ) ) {
                idxflags.indexOnQName = false;

                if( LOG.isTraceEnabled() ) {
                    LOG.trace("cannot use index on QName: {}. Collection {} does not define an index", contextQName, collection.getURI());
                }
            }

            if( !idxflags.hasIndexOnQNames && idxcfg.hasIndexesByQName() ) {
                idxflags.hasIndexOnQNames = true;
            }

            if( !idxflags.hasIndexOnPaths && idxcfg.hasIndexesByPath() ) {
                idxflags.hasIndexOnPaths = true;
            }
        }
        return( idxflags );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#resetState()
     */
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


    public void accept( ExpressionVisitor visitor )
    {
        visitor.visitGeneralComparison( this );
    }

    public final static class IndexFlags
    {
        public boolean indexOnQName     = true;
        public boolean partialIndexOnQName = false;
        public boolean hasIndexOnPaths  = false;
        public boolean hasIndexOnQNames = false;

        public boolean indexOnQName()
        {
            return( indexOnQName );
        }


        public boolean hasIndexOnPaths()
        {
            return( hasIndexOnPaths );
        }


        public boolean hasIndexOnQNames()
        {
            return( hasIndexOnQNames );
        }


        public void reset( boolean indexOnQName )
        {
            this.indexOnQName     = indexOnQName;
            this.partialIndexOnQName = false;
            this.hasIndexOnPaths  = false;
            this.hasIndexOnQNames = false;
        }
    }
}
