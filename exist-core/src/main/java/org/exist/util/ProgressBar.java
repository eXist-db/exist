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
		System.out.print(buf);
		mPosition = pos;
	}
}
