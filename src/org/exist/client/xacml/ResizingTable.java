package org.exist.client.xacml;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

public class ResizingTable extends JTable
{
	public ResizingTable()
	{
		super();
		initColumns();
	}
	public ResizingTable(TableModel tableModel, TableColumnModel columnModel)
	{
		super(tableModel, columnModel);
	}
	public ResizingTable(TableModel model)
	{
		super(model);
		initColumns();
	}
	private void initColumns()
	{
		setIntercellSpacing(new Dimension(3,3));
		setAutoResizeMode(AUTO_RESIZE_OFF);
		setColumnWidths();
	}
	
	public void tableChanged(TableModelEvent event)
	{
		super.tableChanged(event);
		//fix for a NullPointerException when setModel from JTable constructor
		//	generates a tableChanged event
		if(defaultRenderersByColumnClass != null)
			setColumnWidths();
	}
	
	private void setColumnWidths()
	{
		if(columnModel == null)
			return;

		int[] width = new int[getColumnCount()];

		getHeaderWidths(width);
		getCellMaxWidths(width);
		setCellWidths(width, false);
	}
	private void getHeaderWidths(int[] width)
	{
		JTableHeader header = getTableHeader();
		for(int column = 0; column < width.length; column++)
		{
			TableCellRenderer renderer = columnModel.getColumn(column).getHeaderRenderer();
			if(renderer == null)
			{
				if(header == null)
					continue;
				renderer = header.getDefaultRenderer();
				if(renderer == null)
					continue;
			}

			Component comp = renderer.getTableCellRendererComponent(this, getColumnName(column), false, false, -1, column);
			Dimension prefSize = comp.getPreferredSize();
			if(prefSize.width > width[column])
				width[column] = prefSize.width;
		}
	}
	private void setCellWidths(int[] width, boolean override)
	{
		int spacingWidth = getIntercellSpacing().width * 2;
		
		for(int column = 0; column < width.length; column++)
		{
			TableColumn tableColumn = columnModel.getColumn(column);
			int newWidth = width[column] + spacingWidth + 6;
			if(override)
				tableColumn.setPreferredWidth(newWidth);
			else
			{
				int currentWidth = tableColumn.getWidth();
				tableColumn.setPreferredWidth(Math.max(currentWidth, newWidth));
			}
		}
	}
	private void getCellMaxWidths(int[] width)
	{
		int rowCount = getRowCount();
		for(int row = 0; row < rowCount; row++)
		{
			for(int column = 0; column < width.length; column++)
			{
				TableCellRenderer renderer = getCellRenderer(row, column);
				if(renderer == null)
					renderer = getDefaultRenderer(getModel().getColumnClass(column));
				if(renderer == null)
					continue;
				
				Object value = getValueAt(row, column);
				boolean isSelected = isCellSelected(row, column);
				Component comp = renderer.getTableCellRendererComponent(this, value, isSelected, false, row, column);
				width[column] = Math.max(comp.getPreferredSize().width, width[column]);
			}
		}
	}
	public Dimension getPreferredScrollableViewportSize()
	{
		Dimension prefSize = getPreferredSize();
		Dimension maxSize = getMaximumSize();
		Dimension minSize = getMinimumSize();
		int width = Math.max(Math.min(maxSize.width, prefSize.width), minSize.width);
		int height = Math.max(Math.min(maxSize.height, prefSize.height), minSize.height);
		return new Dimension(width, height);
	}
	public boolean getScrollableTracksViewportWidth()
	{
		return getPreferredSize().width < getParent().getSize().width;
	}
}