package org.exist.xquery.modules.expathrepo;

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 *
 * @author aretter
 */


public class EXPathErrorCode extends ErrorCode {
    
    /**
     * EXPATH specific errors [EXP][DY|SE|ST][nnnn]
     * 
     * EXP = EXPath
     * DY = Dynamic
     * DY = Dynamic
     * SE = Serialization
     * ST = Static
     * nnnn = number
     */
    public final static ErrorCode EXPDY001 = new EXPathErrorCode("EXPATH001", "Package not found.");
    public final static ErrorCode EXPDY002 = new EXPathErrorCode("EXPATH002", "Bad collection URI.");
    public final static ErrorCode EXPDY003 = new EXPathErrorCode("EXPATH003", "Permission denied.");
    public final static ErrorCode EXPDY004 = new EXPathErrorCode("EXPATH004", "Error in descriptor found.");
    public final static ErrorCode EXPDY005 = new EXPathErrorCode("EXPATH005", "Invalid repo URI");
    public final static ErrorCode EXPDY006 = new EXPathErrorCode("EXPATH006", "Failed to connect to public repo");
    // other error thrown from expath library
    public final static ErrorCode EXPDY007 = new EXPathErrorCode("EXPATH00", null);
    
    public final static String EXPATH_ERROR_NS = "http://expath.org/ns/error";
    public final static String EXPATH_ERROR_PREFIX = "experr";
    
    private EXPathErrorCode(String code, String description) {
        super(new QName(code, EXPATH_ERROR_NS, EXPATH_ERROR_PREFIX), description);
    }
}