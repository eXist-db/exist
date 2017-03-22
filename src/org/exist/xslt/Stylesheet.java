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

import java.io.IOException;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.xml.sax.SAXException;

/**
 * {@link javax.xml.transform.Templates} resolver and compiler interface.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public interface Stylesheet {

  <E extends Exception> Templates templates(DBBroker broker, XSLTErrorsListener<E> errorListener)
      throws E, PermissionDeniedException, SAXException, TransformerConfigurationException, IOException;

  <E extends Exception> TransformerHandler newTransformerHandler(DBBroker broker, XSLTErrorsListener<E> errorListener)
      throws E, PermissionDeniedException, SAXException, TransformerConfigurationException, IOException;
}
