package org.exist.launcher;

import javax.annotation.Nullable;

/**
 * Interface for managing platform native Services.
 *
 * @author Adam Retter
 */
public interface ServiceManager {

    //TODO(AR) expand to support multiple services by adding a Service interface and pass that as a parameter to each function below

    /**
     * Installs the Service.
     *
     * If the service is already installed an exception is raised.
     *
     * @throws ServiceManagerException if an error occurs whilst installing the service.
     */
    void install() throws ServiceManagerException;

    /**
     * Returns true if the Service is installed.
     *
     * @return true if the service is installed, false otherwise.
     */
    boolean isInstalled();

    /**
     * Uninstalls the Service.
     *
     * If the service is already uninstalled an exception is raised.
     *
     * @throws ServiceManagerException if an error occurs whilst uninstalling the service.
     */
    void uninstall() throws ServiceManagerException;

    /**
     * Starts the Service.
     *
     * If the service is already started, this is a noop
     *
     * @throws ServiceManagerException if an error occurs whilst starting the service.
     */
    void start() throws ServiceManagerException;

    /**
     * Returns true if the Service is running
     *
     * @return true if the service is running, false otherwise.
     */
    boolean isRunning();

    /**
     * Stops the Service.
     *
     * If the service is already stopped, this is a noop
     *
     * @throws ServiceManagerException if an error occurs whilst stopping the service.
     */
    void stop() throws ServiceManagerException;

    /**
     * Show the platforms native Service Management console.
     *
     * @throws UnsupportedOperationException if the service manager
     *      does not support showing the platforms native Service
     *      ManagementConsole.
     *
     * @throws ServiceManagerException if an error occurs opening the console.
     */
    void showNativeServiceManagementConsole() throws UnsupportedOperationException, ServiceManagerException;
}
