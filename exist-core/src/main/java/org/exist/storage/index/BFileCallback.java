package org.exist.storage.index;

import org.exist.storage.btree.Value;

public interface BFileCallback {

    public void info(Value key, Value value);

}
