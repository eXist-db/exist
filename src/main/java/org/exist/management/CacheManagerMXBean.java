package org.exist.management;

import org.exist.management.impl.PerInstanceMBean;

public interface CacheManagerMXBean extends PerInstanceMBean {

    long getMaxTotal();

    long getMaxSingle();

    long getCurrentSize();
}
