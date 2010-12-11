package org.exist.util.io;

import java.io.IOException;

/**
 *
 * @author aretter
 */
public interface FilterInputStreamCache {
    
    public void write(byte b[], int off, int len) throws IOException;

    public void write(int i) throws IOException;

    public byte get(int off) throws IOException;

    public int getLength();

    public void copyTo(int cacheOffset, byte b[], int off, int len) throws IOException;

    public void invalidate() throws IOException;
}
