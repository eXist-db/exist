
package org.exist.util;

/*
 *  eXist xml document repository and xpath implementation
 *  Copyright (C) 2001,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    27. Juni 2002
 */
public class ProgressIndicator {

    protected double mMax = 1;
    protected double mValue = 0;


    /**
     *  Constructor for the ProgressIndicator object
     *
     *@param  max  Description of the Parameter
     */
    public ProgressIndicator( double max ) {
        mMax = max;
    }


    /**
     *  Sets the value attribute of the ProgressIndicator object
     *
     *@param  value  The new value value
     */
    public void setValue( double value ) {
        mValue = value;
    }


    /**
     *  Gets the percentage attribute of the ProgressIndicator object
     *
     *@return    The percentage value
     */
    public int getPercentage() {
        return (int) ( ( mValue / mMax ) * 100 );
    }


    /**
     *  Gets the max attribute of the ProgressIndicator object
     *
     *@return    The max value
     */
    public double getMax() {
        return mMax;
    }


    /**
     *  Gets the value attribute of the ProgressIndicator object
     *
     *@return    The value value
     */
    public double getValue() {
        return mValue;
    }
}

