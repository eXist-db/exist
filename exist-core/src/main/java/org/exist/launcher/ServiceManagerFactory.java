package org.exist.launcher;

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import com.evolvedbinary.j8fu.lazy.AtomicLazyValE;
import org.apache.commons.lang3.SystemUtils;

import javax.annotation.Nullable;

public class ServiceManagerFactory {

    private static final AtomicLazyVal<ServiceManager> WINDOWS_SERVICE_MANAGER = new AtomicLazyVal<>(() -> new WindowsServiceManager());

    /**
     * Returns the service manager for the current
     * platform or null if the platform is unsupported.
     *
     * @return the service manager, or null if the platform is unsupported.
     */
    public static @Nullable ServiceManager getServiceManager() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return WINDOWS_SERVICE_MANAGER.get();
        }

        return null;
    }
}
