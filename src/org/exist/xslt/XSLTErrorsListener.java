/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xslt;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public abstract class XSLTErrorsListener<E extends Exception> implements ErrorListener {

  private final static Logger LOG = LogManager.getLogger(XSLTErrorsListener.class);

  private final static int NO_ERROR = 0;
  private final static int WARNING = 1;
  private final static int ERROR = 2;
  private final static int FATAL = 3;

  boolean stopOnError;
  boolean stopOnWarn;

  private int errorCode = NO_ERROR;
  private Exception exception;

  public XSLTErrorsListener(boolean stopOnError, boolean stopOnWarn) {
    this.stopOnError = stopOnError;
    this.stopOnWarn = stopOnWarn;
  }

  protected abstract void raiseError(String error, Exception ex) throws E;

  public void checkForErrors() throws E {
    switch (errorCode) {
      case WARNING:
        if (stopOnWarn) {
          raiseError("XSL transform reported warning: " + exception.getMessage(), exception);
        }
        break;
      case ERROR:
        if (stopOnError) {
          raiseError("XSL transform reported error: " + exception.getMessage(), exception);
        }
        break;
      case FATAL:
        raiseError("XSL transform reported error: " + exception.getMessage(), exception);
    }
  }

  public void warning(TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports warning: " + except.getMessage(), except);
    errorCode = WARNING;
    exception = except;
    if (stopOnWarn) {
      throw except;
    }
  }

  public void error(TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports recoverable error: " + except.getMessage(), except);
    errorCode = ERROR;
    exception = except;
    if (stopOnError) {
      throw except;
    }
  }

  public void fatalError(TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports fatal error: " + except.getMessage(), except);
    errorCode = FATAL;
    exception = except;
    throw except;
  }
}
