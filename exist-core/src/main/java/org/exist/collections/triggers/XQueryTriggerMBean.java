package org.exist.collections.triggers;

public interface XQueryTriggerMBean {
    int getKeys();

    void clear();

    String dumpTriggerStates();

    String listKeys();
}
