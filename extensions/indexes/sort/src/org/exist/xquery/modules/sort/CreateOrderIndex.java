package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.indexing.sort.SortItem;
import org.exist.util.FastQSort;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class CreateOrderIndex extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("create-index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                "Create a sort index to be used within an 'order by' expression.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The id by which the index will be known and distinguished from other indexes " +
                        "on the same nodes."),
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                        "The node set to be indexed."),
                    new FunctionParameterSequenceType("values", Type.ATOMIC, Cardinality.ZERO_OR_MORE,
                        "The values to be indexed. There should be one value for each node in $nodes. " +
                        "$values thus needs to contain as many items as $nodes. If not, a dynamic error " +
                        "is triggered."),
                    new FunctionParameterSequenceType("options", Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                        "<options order='ascending|descending' empty='least|greatest'/>")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "")),
        new FunctionSignature(
                new QName("create-index-callback", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                "Create a sort index to be used within an 'order by' expression.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The id by which the index will be known and distinguished from other indexes " +
                        "on the same nodes."),
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                        "The node set to be indexed."),
                    new FunctionParameterSequenceType("callback",Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE,
                        "A callback function which will be called for every node in the $nodes input set. " +
                        "The function receives the current node as single argument and should return " +
                        "an atomic value by which the node will be sorted."),
                    new FunctionParameterSequenceType("options", Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                        "<options order='ascending|descending' empty='least|greatest'/>")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, ""))
    };

    protected static final Logger LOG = Logger.getLogger(CreateOrderIndex.class);

    private boolean descending = false;
    private boolean emptyLeast = false;

    public CreateOrderIndex(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        String id = args[0].getStringValue();
        // check how the function was called and prepare callback
        FunctionReference call = null;
        if (isCalledAs("create-index-callback")) {
            call = (FunctionReference) args[2].itemAt(0);
        } else if (args[2].getItemCount() != args[1].getItemCount())
            throw new XPathException(this, "$nodes and $values sequences need to have the same length.");

        // options
        if (args[3].getItemCount() > 0) {
            NodeValue optionValue = (NodeValue) args[3].itemAt(0);
            Element options = (Element) optionValue.getNode();
            String option = options.getAttribute("order");
            if (option != null) {
                descending = option.equalsIgnoreCase("descending");
            }
            option = options.getAttribute("empty");
            if (option != null) {
                emptyLeast = option.equalsIgnoreCase("least");
            }
        }
        
        // create the input list to be sorted below
        List<SortItem> items = new ArrayList<SortItem>(args[1].getItemCount());
        Sequence params[] = new Sequence[1];
        SequenceIterator valuesIter = null;
        if (call == null)
            valuesIter = args[2].iterate();
	    int c = 0;
        int len = args[1].getItemCount();
        
        int logChunk = 1 + (len / 20);
        
        for (SequenceIterator nodesIter = args[1].iterate(); nodesIter.hasNext(); ) {
            NodeValue nv = (NodeValue) nodesIter.nextItem();
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                throw new XPathException(this, "Cannot create order-index on an in-memory node");
            NodeProxy node = (NodeProxy) nv;
            SortItem si = new SortItemImpl(node);
	        
            
            
            if (LOG.isDebugEnabled() && ++c % logChunk == 0) {
		        LOG.debug("Storing item " + c + " out of " + len + " to sort index.");
            }
            
            if (call != null) {
                // call the callback function to get value
                params[0] = node;
                Sequence r = call.evalFunction(contextSequence, null, params);
                if (!r.isEmpty()) {
                    AtomicValue v = r.itemAt(0).atomize();
                    if (v.getType() == Type.UNTYPED_ATOMIC)
                        v = v.convertTo(Type.STRING);
                    si.setValue(v);
                }
            } else {
                // no callback, take value from second sequence
                AtomicValue v = valuesIter.nextItem().atomize();
                if (v.getType() == Type.UNTYPED_ATOMIC)
                    v = v.convertTo(Type.STRING);
                si.setValue(v);
            }
            items.add(si);
        }
        // sort the set
        FastQSort.sort(items, 0, items.size() - 1);
        // create the index
        SortIndexWorker index = (SortIndexWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        try {
            index.createIndex(id, items);
        } catch (EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (LockException e) {
            throw new XPathException(this, "Caught lock error while creating index. Giving up.", e);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private class SortItemImpl implements SortItem {

        NodeProxy node;
        AtomicValue value = AtomicValue.EMPTY_VALUE;

        public SortItemImpl(NodeProxy node) {
            this.node = node;
        }

        public NodeProxy getNode() {
            return node;
        }

        public void setValue(AtomicValue value) {
            if (value.hasOne())
                this.value = value;
        }

        public AtomicValue getValue() {
            return value;
        }

        public int compareTo(SortItem other) {
            int cmp = 0;
            AtomicValue a = this.value;
            AtomicValue b = other.getValue();
            boolean aIsEmpty = (a.isEmpty() || (Type.subTypeOf(a.getType(), Type.NUMBER) && ((NumericValue) a).isNaN()));
            boolean bIsEmpty = (b.isEmpty() || (Type.subTypeOf(b.getType(), Type.NUMBER) && ((NumericValue) b).isNaN()));
            if (aIsEmpty) {
                if (bIsEmpty)
                    // both values are empty
                    return Constants.EQUAL;
                else if (emptyLeast)
                    cmp = Constants.INFERIOR;
                else
                    cmp = Constants.SUPERIOR;
            } else if (bIsEmpty) {
                // we don't need to check for equality since we know a is not empty
                if (emptyLeast)
                    cmp = Constants.SUPERIOR;
                else
                    cmp = Constants.INFERIOR;
            } else if (a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
                if(emptyLeast)
                    cmp = Constants.INFERIOR;
                else
                    cmp = Constants.SUPERIOR;
            } else if (b == AtomicValue.EMPTY_VALUE && a != AtomicValue.EMPTY_VALUE) {
                if(emptyLeast)
                    cmp = Constants.SUPERIOR;
                else
                    cmp = Constants.INFERIOR;
            } else
                cmp = a.compareTo(b);
            if(descending)
                cmp = cmp * -1;
            return cmp;
        }
    }
}