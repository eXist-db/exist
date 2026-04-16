/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xslt;

import javax.annotation.Nullable;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public abstract class XSLTErrorsListener<E extends Exception> implements ErrorListener {

  private static final Logger LOG = LogManager.getLogger(XSLTErrorsListener.class);

  private enum ErrorType {
    WARNING, ERROR, FATAL
  }

  private final boolean stopOnError;
  private final boolean stopOnWarn;

  @Nullable private ErrorType errorType;
  @Nullable private TransformerException exception;

  public XSLTErrorsListener(final boolean stopOnError, final boolean stopOnWarn) {
    this.stopOnError = stopOnError;
    this.stopOnWarn = stopOnWarn;
  }

  protected abstract void raiseError(String error, TransformerException ex) throws E;

  public void checkForErrors() throws E {
    if (errorType == null) {
      return;
    }

    switch (errorType) {
      case WARNING:
        if (stopOnWarn) {
          raiseError("XSL transform reported warning: " + exception.getMessageAndLocation(), exception);
        }
        break;
      case ERROR:
        if (stopOnError) {
          raiseError("XSL transform reported error: " + exception.getMessageAndLocation(), exception);
        }
        break;
      case FATAL:
        raiseError("XSL transform reported error: " + exception.getMessageAndLocation(), exception);
    }
  }

  @Override
  public void warning(final TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports warning: {}", except.getMessage(), except);
    errorType = ErrorType.WARNING;
    exception = except;
    if (stopOnWarn) {
      throw except;
    }
  }

  @Override
  public void error(final TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports recoverable error: {}", except.getMessage(), except);
    errorType = ErrorType.ERROR;
    exception = except;
    if (stopOnError) {
      throw except;
    }
  }

  @Override
  public void fatalError(final TransformerException except) throws TransformerException {
    LOG.warn("XSL transform reports fatal error: {}", except.getMessage(), except);
    errorType = ErrorType.FATAL;
    exception = except;
    throw except;
  }
}
