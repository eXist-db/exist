package org.exist.indexing.sort;

import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.value.AtomicValue;

public interface SortItem extends Comparable<SortItem> {

    AtomicValue getValue();

    void setValue(AtomicValue value);

    NodeProxy getNode();

    int compareTo(SortItem sortItem);
}
