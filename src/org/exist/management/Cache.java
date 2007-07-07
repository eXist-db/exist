package org.exist.management;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Jun 9, 2007
 * Time: 10:05:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class Cache implements CacheMBean {

    private org.exist.storage.cache.Cache cache;

    public Cache(org.exist.storage.cache.Cache cache) {
        this.cache = cache;
    }

    public String getType() {
        return cache.getType();
    }

    public int getSize() {
        return cache.getBuffers();
    }

    public int getUsed() {
        return cache.getUsedBuffers();
    }

    public int getHits() {
        return cache.getHits();
    }

    public int getFails() {
        return cache.getFails();
    }

    public String getFileName() {
        return cache.getFileName();
    }
}
