package org.exist.storage;


/**
 * Created by aretter on 20/08/2016.
 */
public class BrokerPoolServiceException extends Exception {
    public BrokerPoolServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BrokerPoolServiceException(final Throwable cause) {
        super(cause);
    }
}
