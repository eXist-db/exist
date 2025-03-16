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

package org.exist.util;

/**
 * Implements correct checks for XML names and NCNames.
 *
 * @author Wolfgang
 */
public class XMLNames {

    /**
     * Determines if a character is an XML name start character.
     * See https://www.w3.org/TR/REC-xml/#NT-Name.
     *
     * @param codePoint the code point
     * @return true if the character is an XML Name start character
     */
    public static boolean isXMLNameStartCharacter(final int codePoint) {
        return codePoint == ':'
                || codePoint >= 'A' && codePoint <= 'Z'
                || codePoint == '_'
                || codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= 0xC0 && codePoint <= 0xD6
                || codePoint >= 0xD8 && codePoint <= 0xF6
                || codePoint >= 0xF8 && codePoint <= 0x2FF
                || codePoint >= 0x370 && codePoint <= 0x37D
                || codePoint >= 0x37F && codePoint <= 0x1FFF
                || codePoint >= 0x200C && codePoint <= 0x200D
                || codePoint >= 0x2070 && codePoint <= 0x218F
                || codePoint >= 0x2C00 && codePoint <= 0x2FEF
                || codePoint >= 0x3001 && codePoint <= 0xD7FF
                || codePoint >= 0xF900 && codePoint <= 0xFDCF
                || codePoint >= 0xFDF0 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0xEFFFF;

    }

    /**
     * Determines if a character is an XML name character.
     * See https://www.w3.org/TR/REC-xml/#NT-Name.
     *
     * @param codePoint the code point
     * @return true if the character is an XML Name character
     */
    public static boolean isXMLNameChar(final int codePoint) {
        return isXMLNameStartCharacter(codePoint)
                || codePoint == '-'
                || codePoint == '.'
                || codePoint >= '0' && codePoint <= '9'
                || codePoint == 0xB7
                || codePoint >= 0x0300 && codePoint <= 0x036F
                || codePoint >= 0x203F && codePoint <= 0x2040;
    }


    /**
     * Deterimines if a character is an NCName start character.
     *
     * See https://www.w3.org/TR/REC-xml-names/#NT-NCName
     *
     * @param codePoint the code point
     * @return true if the character is an XML NCName start character
     */
    public static boolean isNCNameStartChar(final int codePoint) {
        return codePoint != ':' && isXMLNameStartCharacter(codePoint);
    }

    /**
     * Deterimines if a character is an NCName (Non-Colonised Name) character.
     *
     * See https://www.w3.org/TR/REC-xml-names/#NT-NCName
     *
     * @param codePoint the code point
     * @return true if the character is an XML NCName character
     */
    public static boolean isNCNameChar(final int codePoint) {
        return codePoint != ':' && isXMLNameChar(codePoint);
    }

    /**
     * Check if the provided string is a valid xs:NCName.
     *
     * See https://www.w3.org/TR/REC-xml-names/#NT-NCName
     *
     * @param s the string
     * @return true if the string is a valid XML NCName
     */
    public static  boolean isNCName(final CharSequence s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int firstCodePoint = Character.codePointAt(s, 0);
        if(!isNCNameStartChar(firstCodePoint)) {
            return false;
        }
        for(int i = Character.charCount(firstCodePoint); i < s.length(); ) {
            final int codePoint = Character.codePointAt(s, i);
            if(!isNCNameChar(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isName(final CharSequence s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int firstCodePoint = Character.codePointAt(s, 0);
        if(!isXMLNameStartCharacter(firstCodePoint)) {
            return false;
        }
        for(int i = Character.charCount(firstCodePoint); i < s.length(); ) {
            final int codePoint = Character.codePointAt(s, i);
            if(!isXMLNameChar(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isNmToken(final CharSequence s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for(int i = 0; i < s.length(); ) {
            final int codePoint = Character.codePointAt(s, i);
            if(!isXMLNameChar(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }
}
