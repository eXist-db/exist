package org.exist.storage.dom;


import org.exist.dom.persistent.IStoredNode;
import java.io.Closeable;
import java.util.Iterator;

public interface INodeIterator extends Iterator<IStoredNode>, Closeable {

}
