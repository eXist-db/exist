package org.exist.management;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class CacheManager implements CacheManagerMXBean {
    private final String instanceId;
    private final org.exist.storage.CacheManager manager;

    public CacheManager(final String instanceId, final org.exist.storage.CacheManager manager) {
        this.instanceId = instanceId;
        this.manager = manager;
    }

    public static String getAllInstancesQuery() {
        return "org.exist.management." + '*' + ":type=CacheManager";
    }

    private static ObjectName getName(final String instanceId) throws MalformedObjectNameException {
        return new ObjectName("org.exist.management." + instanceId + ":type=CacheManager");
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return getName(instanceId);
    }

    @Override
    public String getInstanceId() {
        return instanceId;
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
