package org.exist.storage.store;

import org.dbxml.core.data.Value;

public interface BFileCallback {

    public void info(Value key, Value value);

}
