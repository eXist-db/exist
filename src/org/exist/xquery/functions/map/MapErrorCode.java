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