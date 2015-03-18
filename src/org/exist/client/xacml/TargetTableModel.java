/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.client.xacml;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.xacml.XACMLConstants;
import org.exist.security.xacml.XACMLUtil;

import com.sun.xacml.TargetMatch;
import com.sun.xacml.UnknownIdentifierException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.cond.Evaluatable;
import com.sun.xacml.cond.Function;
import com.sun.xacml.cond.FunctionFactory;
import com.sun.xacml.cond.FunctionTypeException;


public class TargetTableModel implements TableModel
{
	private static final Logger LOG = LogManager.getLogger(TargetTableModel.class);
	private static final String UNSPECIFIED = "[match all]";
	
	private static final AttributeDesignator[] SUBJECT_ATTRIBUTES;
	private static final AttributeDesignator[] ACTION_ATTRIBUTES;
	private static final AttributeDesignator[] RESOURCE_ATTRIBUTES;
	private static final AttributeDesignator[] ENVIRONMENT_ATTRIBUTES;
	
	static
	{
		SUBJECT_ATTRIBUTES = new AttributeDesignator[4];
		SUBJECT_ATTRIBUTES[0] = new AttributeDesignator(AttributeDesignator.SUBJECT_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.SUBJECT_ID_ATTRIBUTE, false);
		SUBJECT_ATTRIBUTES[1] = new AttributeDesignator(AttributeDesignator.SUBJECT_TARGET, XACMLConstants.URI_TYPE, XACMLConstants.SUBJECT_NS_ATTRIBUTE, false);
		SUBJECT_ATTRIBUTES[2] = new AttributeDesignator(AttributeDesignator.SUBJECT_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.GROUP_ATTRIBUTE, false);
		SUBJECT_ATTRIBUTES[3] = new AttributeDesignator(AttributeDesignator.SUBJECT_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.USER_NAME_ATTRIBUTE, false);
		
		ACTION_ATTRIBUTES = new AttributeDesignator[2];
		ACTION_ATTRIBUTES[0] = new AttributeDesignator(AttributeDesignator.ACTION_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.ACTION_ID_ATTRIBUTE, false);
		ACTION_ATTRIBUTES[1] = new AttributeDesignator(AttributeDesignator.ACTION_TARGET, XACMLConstants.URI_TYPE, XACMLConstants.ACTION_NS_ATTRIBUTE, false);
		
		RESOURCE_ATTRIBUTES = new AttributeDesignator[6];
		RESOURCE_ATTRIBUTES[0] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.RESOURCE_ID_ATTRIBUTE, false);
		RESOURCE_ATTRIBUTES[1] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.URI_TYPE, XACMLConstants.MODULE_NS_ATTRIBUTE, false);
		RESOURCE_ATTRIBUTES[2] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.RESOURCE_CATEGORY_ATTRIBUTE, false);
		RESOURCE_ATTRIBUTES[3] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.MODULE_CATEGORY_ATTRIBUTE, false);
		RESOURCE_ATTRIBUTES[4] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.SOURCE_KEY_ATTRIBUTE, false);
		RESOURCE_ATTRIBUTES[5] = new AttributeDesignator(AttributeDesignator.RESOURCE_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.SOURCE_TYPE_ATTRIBUTE, false);
		
		ENVIRONMENT_ATTRIBUTES = new AttributeDesignator[4];
		ENVIRONMENT_ATTRIBUTES[0] = new AttributeDesignator(AttributeDesignator.ENVIRONMENT_TARGET, XACMLConstants.DATE_TYPE, XACMLConstants.CURRENT_DATE_ATTRIBUTE, false);
		ENVIRONMENT_ATTRIBUTES[1] = new AttributeDesignator(AttributeDesignator.ENVIRONMENT_TARGET, XACMLConstants.TIME_TYPE, XACMLConstants.CURRENT_TIME_ATTRIBUTE, false);
		ENVIRONMENT_ATTRIBUTES[2] = new AttributeDesignator(AttributeDesignator.ENVIRONMENT_TARGET, XACMLConstants.DATETIME_TYPE, XACMLConstants.CURRENT_DATETIME_ATTRIBUTE, false);
		ENVIRONMENT_ATTRIBUTES[3] = new AttributeDesignator(AttributeDesignator.ENVIRONMENT_TARGET, XACMLConstants.STRING_TYPE, XACMLConstants.ACCESS_CONTEXT_ATTRIBUTE, false);
	}
	
	private int type;
	private List<TableModelListener> listeners;
	private AttributeDesignator[] attributes;
	private Abbreviator abbrev;
	
	private AttributeValue[][] values;
	private URI[][] functions;

	
	@SuppressWarnings("unused")
	private TargetTableModel() {}
	public TargetTableModel(int type, Abbreviator abbrev)
	{
		this.abbrev = abbrev;
		this.type = type;
		attributes = getAttributes(type);
		values = new AttributeValue[0][0];
		functions = new URI[0][0];
	}
	private static AttributeDesignator[] getAttributes(int type)
	{
		switch(type)
		{
			case AttributeDesignator.ACTION_TARGET:
				return ACTION_ATTRIBUTES;
			case AttributeDesignator.RESOURCE_TARGET:
				return RESOURCE_ATTRIBUTES;
			case AttributeDesignator.SUBJECT_TARGET:
				return SUBJECT_ATTRIBUTES;
			case AttributeDesignator.ENVIRONMENT_TARGET:
				return ENVIRONMENT_ATTRIBUTES;
			default:
				throw new IllegalArgumentException("Invalid target type");
		}
	}
	
	//TableModel method implementations
	public int getColumnCount()
	{
		return attributes.length;
	}

	public int getRowCount()
	{
		return values.length + 1;
	}

	public boolean isCellEditable(int row, int col)
	{
		return false;
	}

	public Class<?> getColumnClass(int col)
	{
		return String.class;
	}

	public Object getValueAt(int row, int col)
	{
		if(row == values.length)
			{return "";}
		final AttributeValue value = values[row][col];
		if(value == null)
			{return UNSPECIFIED;}
		
		final URI functionId = functions[row][col];
		if(functionId == null)
			{return UNSPECIFIED;}
		final String functionString = abbrev.getAbbreviatedTargetFunctionId(functionId, attributes[col].getType());
		if(functionString == null)
		{
			LOG.warn("Abbreviated function string was unexpectedly null.  FunctionId URI was '" + functionId + "' (Row " + row + ", column " + col + ")");
			return UNSPECIFIED;
		}
		final String stringValue = value.encode();
		if(stringValue == null)
		{
			LOG.warn("String representation of a non-null attribute value was unexpectedly null.  (Row " + row + ", column " + col + ")");
			return UNSPECIFIED;
		}
		return "<html>" + XACMLUtil.XMLEscape(functionString) + "&nbsp;<b>" + XACMLUtil.XMLEscape(stringValue) + "</b>";
	}

	public void setValueAt(Object value, int row, int col)
	{
		//do nothing
	}
	
	public AttributeDesignator getAttribute(int col)
	{
		return attributes[col];
	}
	public URI getFunctionId(int row, int col)
	{
		return (row == values.length) ? null : functions[row][col];
	}
	public AttributeValue getValue(int row, int col)
	{
		return (row == values.length) ? null : values[row][col];
	}
			
	public void setValue(URI functionId, AttributeValue value, int row, int col)
	{
		TableModelEvent event;
		if(row == values.length)
		{
			if(value != null)
			{
				//add a new row if a value was entered into the last row
				addRow();
				event = new TableModelEvent(this, row, row, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT);
			}
			else
				{return;}
		}
		else
			{event = new TableModelEvent(this, row, row, col, TableModelEvent.UPDATE);}
		
		values[row][col] = value;
		functions[row][col] = functionId;
		
		//remove row if empty
		if(value == null && row < values.length)
		{
			boolean empty = true;
			for(int i = 0; i < attributes.length && empty; ++i)
			{
				if(values[row][i] != null)
					{empty = false;}
			}
			if(empty)
			{
				removeRow(row);
				return;
			}
		}
		fireTableChanged(event);
	}
	private void addRow()
	{
		URI[][] newF = new URI[functions.length + 1][];
		System.arraycopy(functions, 0, newF, 0, functions.length);
		newF[functions.length] = new URI[attributes.length];
		functions = newF;

		AttributeValue[][] newV = new AttributeValue[values.length + 1][];
		System.arraycopy(values, 0, newV, 0, values.length);
		newV[values.length] = new AttributeValue[attributes.length];
		values = newV;
	}
	public void removeRow(int row)
	{
		if(functions.length == 0 || functions.length >= row || row < 0)
			{return;}
		final int row1 = row+1;
		
		URI[][] newF = new URI[functions.length - 1][];
		System.arraycopy(functions, 0, newF, 0, row);
		System.arraycopy(functions, row1, newF, row1, functions.length - row1);
		functions = newF;
		
		AttributeValue[][] newV = new AttributeValue[values.length - 1][];
		System.arraycopy(values, 0, newV, 0, row);
		System.arraycopy(values, row1, newV, row1, values.length - row1);
		values = newV;

		final TableModelEvent event = new TableModelEvent(this, row, row, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
		fireTableChanged(event);
	}
	public void setTarget(List target)
	{
		final int length = (target == null) ? 0 : target.size();
		functions = new URI[length][attributes.length];
		values = new AttributeValue[length][attributes.length];
		
		for(int i = 0; i < length; ++i)
		{
			final List matches = (List)target.get(i);
			int col = -1;
			for(final Iterator it = matches.iterator(); it.hasNext() && col < 0;)
			{
				final TargetMatch match = (TargetMatch)it.next();
				final Evaluatable attribute = match.getMatchEvaluatable();
				if(attribute instanceof AttributeDesignator)
				{
					col = getIndex((AttributeDesignator)attribute);
					if(col >= 0)
						{setValue(i, col, match);}
				}
			}
		}
		
		fireTableChanged(new TableModelEvent(this));
	}
	private void fireTableChanged(TableModelEvent event)
	{
		if(listeners == null)
			{return;}
		
		for(final TableModelListener listener : listeners)
			listener.tableChanged(event);
	}
	public List<List<TargetMatch>> createTarget()
	{
		final List<List<TargetMatch>> list = new ArrayList<List<TargetMatch>>(values.length);
		for(int row = 0; row < values.length; ++row)
		{
			final List<TargetMatch> matches = new ArrayList<TargetMatch>(attributes.length);
			for(int col = 0; col < attributes.length; ++col)
			{
				final AttributeValue value = values[row][col];
				final URI functionId = functions[row][col];
				if(value != null && functionId != null)
				{
					Function f;
					try
					{
						f = FunctionFactory.getTargetInstance().createFunction(functionId);
						if(f != null)
							{matches.add(new TargetMatch(type, f, attributes[col], value));}
					}
					catch (final UnknownIdentifierException e)
					{
						LOG.warn(e);
					}
					catch (final FunctionTypeException e)
					{
						LOG.warn(e);
					}
				}
			}
			if(matches.size() > 0)
				{list.add(matches);}
		}
		return (list.size() > 0) ? list : null; 
			
	}
	
	public void setValue(int row, int col, TargetMatch match)
	{
		final AttributeValue value = match.getMatchValue();
		final URI functionId = match.getMatchFunction().getIdentifier();
		setValue(functionId, value, row, col);
	}
	public int getIndex(AttributeDesignator attribute)
	{
		for(int i = 0; i < attributes.length; ++i)
		{
			if(equals(attribute, (attributes[i])))
				{return i;}
		}
		return -1;
	}
	public static boolean equals(AttributeDesignator one, AttributeDesignator two)
	{
		if(one == null)
			{return two == null;}
		if(two == null)
			{return false;}
		if(!one.getId().equals(two.getId()))
			{return false;}
		if(!one.getType().equals(two.getType()))
			{return false;}
		
		if(!equals(one.getCategory(), two.getCategory()))
			{return false;}
		
		if(!equals(one.getIssuer(), two.getIssuer()))
			{return false;}
		return true;
	}
	public static boolean equals(URI one, URI two)
	{
		if(one == null)
			{return two == null;}
		if(two == null)
			{return false;}
		return one.equals(two);
	}

	public String getColumnName(int pos)
	{
		final URI attributeID = attributes[pos].getId();
		return abbrev.getAbbreviatedId(attributeID);
	}

	public void addTableModelListener(TableModelListener listener)
	{
		if(listener == null)
			{return;}
		if(listeners == null)
			{listeners = new ArrayList<TableModelListener>(2);}
		listeners.add(listener);
	}

	public void removeTableModelListener(TableModelListener listener)
	{
		if(listeners == null || listener == null)
			{return;}
		listeners.remove(listener);
	}
}
