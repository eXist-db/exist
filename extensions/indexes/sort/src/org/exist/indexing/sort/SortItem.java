package org.exist.indexing.sort;

import org.exist.dom.NodeProxy;
import org.exist.xquery.value.AtomicValue;

public class SortItem implements Comparable<SortItem> {

    NodeProxy node;
    AtomicValue value = AtomicValue.EMPTY_VALUE;

    public SortItem(NodeProxy node) {
        this.node = node;
    }

    public void setValue(AtomicValue value) {
        this.value = value;
    }

    public AtomicValue getValue() {
        return value;
    }
    
    public int compareTo(SortItem sortItem) {
        return value.compareTo(sortItem.value);
    }
}
