// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================
package org.exist.start;

/**
 * Utility class for parsing and comparing version strings.
 * JDK 1.1 compatible.
 * @author Jan Hlavat�
 */
 
public class Version {

    private static final int INFERIOR = -1;
    private static final int EQUAL = 0;
    private static final int SUPERIOR = 1;
 
    int _version = 0;
    int _revision = 0;
    int _subrevision = 0;
    String _suffix = "";
    
    public Version() {
    }
    
    public Version(String version_string) {
        parse(version_string);
    }
    
    /**
     * parses version string in the form version[.revision[.subrevision[extension]]]
     * into this instance.
     *
     * @param version_string  Text representation of a version.
     */
    public void parse(String version_string) {
        _version = 0;
        _revision = 0;
        _subrevision = 0;
        _suffix = "";
        int pos = 0;
        int startpos = 0;
        final int endpos = version_string.length();
        while ( (pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
            pos++;
        }
        _version = Integer.parseInt(version_string.substring(startpos,pos));
        if ((pos < endpos) && version_string.charAt(pos)=='.') {
            startpos = ++pos;
            while ( (pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
                pos++;
            }
            _revision = Integer.parseInt(version_string.substring(startpos,pos));
        }
        if ((pos < endpos) && version_string.charAt(pos)=='.') {
            startpos = ++pos;
            while ( (pos < endpos) && Character.isDigit(version_string.charAt(pos))) {
                pos++;
            }
            _subrevision = Integer.parseInt(version_string.substring(startpos,pos));
        }
        if (pos < endpos) {
            _suffix = version_string.substring(pos);
        }
    }
    
    /**
     * @return string representation of this version
     */
    public String toString() {
        return String.valueOf(_version) + '.' + _revision + '.' + _subrevision + _suffix;
    }
    
    // java.lang.Comparable is Java 1.2! Cannot use it
    /**
     * Compares with other version. Does not take extension into account,
     * as there is no reliable way to order them.
     * @param other Version object to be compared.
     * @return Constants.INFERIOR if this is older version that other,
     *         Constants.EQUAL if its same version,
     *         Constants.SUPERIOR if it's newer version than other
     */
    public int compare(Version other) {
        if (other == null) {throw new NullPointerException("other version is null");}
        if (this._version < other._version) {return INFERIOR;}
        if (this._version > other._version) {return SUPERIOR;}
        if (this._revision < other._revision) {return INFERIOR;}
        if (this._revision > other._revision) {return SUPERIOR;}
        if (this._subrevision < other._subrevision) {return INFERIOR;}
        if (this._subrevision > other._subrevision) {return SUPERIOR;}
        return EQUAL;
    }
    
    /**
     * Check whether this version is in range of versions specified
     *
     * @param high Highest version, inclusive.
     * @param low Lowest version, inclusive.
     * @return TRUE if Version is between high and low (inclusive), otherwise FALSE.
     */
    public boolean isInRange(Version low, Version high) {
        return (compare(low)>=0 && compare(high)<=0);
    }
}
