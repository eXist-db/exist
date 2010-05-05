/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNErrorManager {
    
    public static void cancel(String message, SVNLogType logType) throws SVNCancelException {
        cancel(message, Level.FINE, logType);
    }
    
    public static void cancel(String message, Level logLevel, SVNLogType logType) throws SVNCancelException {
        SVNDebugLog.getDefaultLog().log(logType, message, logLevel);
        throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, message));
    }

    public static void authenticationFailed(String message, Object messageObject) throws SVNAuthenticationException {
        authenticationFailed(message, messageObject, Level.FINE);
    }
    
    public static void authenticationFailed(String message, Object messageObject, Level logLevel) throws SVNAuthenticationException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, message, messageObject);
        SVNDebugLog.getDefaultLog().log(SVNLogType.NETWORK, err.getMessage(), logLevel);
        throw new SVNAuthenticationException(err);
    }
    
    public static void error(SVNErrorMessage err, SVNLogType logType) throws SVNException {
        error(err, Level.FINE, logType);
    }
    
    public static void error(SVNErrorMessage err, Level logLevel, SVNLogType logType) throws SVNException {
        if (err == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
        }
        SVNDebugLog.getDefaultLog().log(logType, err.getFullMessage(), logLevel);
        if (err.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err);
        } else if (err.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err);
        } else {
            throw new SVNException(err);
        }
    }
    
    public static void error(SVNErrorMessage err, Throwable cause, SVNLogType logType) throws SVNException {
        error(err, cause, Level.FINE, logType);
    }
    
    public static void error(SVNErrorMessage err, Throwable cause, Level logLevel, SVNLogType logType) throws SVNException {
        if (err == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
        }
        SVNDebugLog.getDefaultLog().log(logType, err.getMessage(), logLevel);
        if (cause != null) {
            SVNDebugLog.getDefaultLog().log(logType, cause, logLevel);
        }
        if (err.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err);
        } else if (err.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err);
        } else {
            throw new SVNException(err, cause);
        }
    }

    public static void error(SVNErrorMessage err1, SVNErrorMessage err2, SVNLogType logType) throws SVNException {
        error(err1, err2, Level.FINE, logType);
    }
    
    public static void error(SVNErrorMessage err1, SVNErrorMessage err2, Level logLevel, SVNLogType logType) throws SVNException {
        if (err1 == null) {
            error(err2, logLevel, logType);
        } else if (err2 == null) {
            error(err1, logLevel, logType);
        }
        err1.setChildErrorMessage(err2);
        SVNDebugLog.getDefaultLog().log(logType, err1.getMessage(), logLevel);
        if (err1.getErrorCode() == SVNErrorCode.CANCELLED || err2.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err1);
        } else if (err1.getErrorCode().isAuthentication() || err2.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err1);
        } 
        throw new SVNException(err1);
    }

    public static void error(SVNErrorMessage err1, SVNErrorMessage err2, Throwable cause, SVNLogType logType) throws SVNException {
        error(err1, err2, cause, Level.FINE, logType);
    }
    
    public static void error(SVNErrorMessage err1, SVNErrorMessage err2, Throwable cause, Level logLevel, SVNLogType logType) throws SVNException {
        if (err1 == null) {
            error(err2, cause, logLevel, logType);
        } else if (err2 == null) {
            error(err1, cause, logLevel, logType);
        }
        err1.setChildErrorMessage(err2);
        SVNDebugLog.getDefaultLog().log(logType, err1.getMessage(), logLevel);
        if (err1.getErrorCode() == SVNErrorCode.CANCELLED || err2.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err1);
        } else if (err1.getErrorCode().isAuthentication() || err2.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err1, cause);
        } 
        throw new SVNException(err1, cause);
    }
}
