
package org.exist.util;

/*
 *  eXist Native XML Database
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
 * This class is used to report information about the parsing progress
 * to registered observers.
 *  
 * @author wolf
 */
public class ProgressIndicator {

    protected float max_ = 1;
    protected float value_ = 0;
	protected int step_ = 1;

	public ProgressIndicator( float max, int step) {
		max_ = max;
		step_ = step;
	}
	
    public ProgressIndicator( float max ) {
        max_ = max;
    }


    /**
     *  Sets the value attribute of the ProgressIndicator object
     *
     *@param  value  The new value value
     */
    public void setValue( float value ) {
        value_ = value;
    }

	public void finish() {
		value_ = max_;
	}
	
    /**
     *  Gets the percentage attribute of the ProgressIndicator object
     *
     *@return    The percentage value
     */
    public int getPercentage() {
        return (int) ( ( value_ / max_ ) * 100 );
    }

	public boolean changed() {
		return value_ % step_ == 0;
	}
	
    /**
     *  Gets the max attribute of the ProgressIndicator object
     *
     *@return    The max value
     */
    public double getMax() {
        return max_;
    }


    /**
     *  Gets the value attribute of the ProgressIndicator object
     *
     *@return    The value value
     */
    public double getValue() {
        return value_;
    }
}

