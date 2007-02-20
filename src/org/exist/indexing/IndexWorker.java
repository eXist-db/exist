package org.exist.indexing;

/**
 * 
 */
public interface IndexWorker {

    void flush();

    StreamListener getListener();
}