
package org.exist.util;

/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

public class ProgressBar {

	protected String mMessage;
	protected double mMax = 1;
	protected int mPosition = 0;

	public ProgressBar(String message) {
		mMessage = message;
	}

	public ProgressBar(String message, double max) {
		mMessage = message;
		mMax = max;
	}

	public void set(double value, double max) {
		mMax = max;
		this.set(value);
	}

	public void set(double value) {
		final int percent = (int)((value / mMax) * 100);
		if(percent % 2 > 0)
			{return;}
		int pos = percent / 2;
		if(pos == mPosition)
			{return;}
		final StringBuilder buf = new StringBuilder();
		buf.append(mMessage);
		buf.append(" [");
		int i = 0;
		for(; i < pos; i++)
			buf.append("=");
		for(; i < 50; i++)
			buf.append(" ");
		buf.append("] (");
		buf.append(percent);
		buf.append(" %)\r");
		System.out.print(buf.toString());
		mPosition = pos;
	}
}
