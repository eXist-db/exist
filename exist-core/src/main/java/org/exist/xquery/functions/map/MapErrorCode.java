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
package org.exist.xquery.functions.map;

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ErrorCodes.ErrorCode;

public class MapErrorCode extends ErrorCodes.ErrorCode
{
  public static final MapErrorCode EXMPDY001 = new MapErrorCode("EXMPDY001", "Key should be a single, atomic value");

  public MapErrorCode(String code, String description) {
    super(code, description);
  }

  public MapErrorCode(QName errorQName, String description) {
    super(errorQName, description);
  }
}