package org.exist.indexing.sort;

import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.value.AtomicValue;

public interface SortItem extends Comparable<SortItem> {

    void setValue(AtomicValue value);

    AtomicValue getValue();

    NodeProxy getNode();
    
    int compareTo(SortItem sortItem);
}
