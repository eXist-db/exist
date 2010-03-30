package org.exist.indexing.sort;

import org.exist.dom.NodeProxy;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;

public interface SortItem extends Comparable<SortItem> {

    public void setValue(AtomicValue value);

    public AtomicValue getValue();

    public NodeProxy getNode();
    
    public int compareTo(SortItem sortItem);
}
