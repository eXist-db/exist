package org.exist.xquery.regex;

/**
 * An iterator over a sequence of unboxed int values
 * 
 * Copied from Saxon-HE 9.2 package net.sf.saxon.regex.
 */
public interface IntIterator {

    /**
     * Test whether there are any more integers in the sequence
     * @return true if there are more integers to come
     */

    public boolean hasNext();

    /**
     * Return the next integer in the sequence. The result is undefined unless hasNext() has been called
     * and has returned true.
     * @return the next integer in the sequence
     */

    public int next();
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
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//

