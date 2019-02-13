package org.exist.management;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Jun 10, 2007
 * Time: 8:31:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class CacheManager implements CacheManagerMXBean {

    private final org.exist.storage.CacheManager manager;

    public CacheManager(org.exist.storage.CacheManager manager) {
        this.manager = manager;
    }

    @Override
    public long getMaxTotal() {
        return manager.getMaxTotal();
    }

    @Override
    public long getMaxSingle() {
        return manager.getMaxSingle();
    }

    @Override
    public long getCurrentSize() {
        return manager.getCurrentSize();
    }
}
