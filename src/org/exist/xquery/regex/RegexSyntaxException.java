package org.exist.xquery.regex;

/**
 * Thrown when an syntactically incorrect regular expression is detected.
 * 
 * Copied from Saxon-HE 9.2 package net.sf.saxon.regex without change.
 */
public class RegexSyntaxException extends Exception {
    private final int position;

    /**
     * Represents an unknown position within a string containing a regular expression.
     */
    public static final int UNKNOWN_POSITION = -1;

    public RegexSyntaxException(String detail) {
        this(detail, UNKNOWN_POSITION);
    }

    public RegexSyntaxException(String detail, int position) {
        super(detail);
        this.position = position;
    }

    /**
     * Returns the index into the regular expression where the error was detected
     * or <code>UNKNOWN_POSITION</code> if this is unknown.
     *
     * @return the index into the regular expression where the error was detected,
     * or <code>UNKNOWNN_POSITION</code> if this is unknown
     */
    public int getPosition() {
        return position;
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//
