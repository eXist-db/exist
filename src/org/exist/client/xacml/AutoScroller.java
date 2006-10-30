package org.exist.client.xacml;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class AutoScroller implements ActionListener
{
	private static final int DELTA = 8;
	private static final int DEFAULT_INCREMENT = 42;
	private static final int DEFAULT_INITIAL_DELAY = 400;
	private static final int DEFAULT_REPEAT_DELAY = 50;
	
	private Component comp;
	private Point cursorLocation;
	private Timer scrollTimer;
	
	public AutoScroller()
	{
		scrollTimer = new Timer(DEFAULT_REPEAT_DELAY, this);
		scrollTimer.setCoalesce(true);
		scrollTimer.setRepeats(true);
		scrollTimer.setInitialDelay(DEFAULT_INITIAL_DELAY);
		scrollTimer.setDelay(DEFAULT_REPEAT_DELAY);
	}

	
	public void stop()
	{
		comp = null;
		cursorLocation = null;
		scrollTimer.stop();
	}
	public void autoscroll(Component comp, Point cursorLocation)
	{
		this.comp = comp;
		this.cursorLocation = cursorLocation;
		scrollTimer.restart();
	}
	public void actionPerformed(ActionEvent event)
	{
		Container parent = comp.getParent();
		if(!(parent instanceof JViewport))
			return;
		JViewport view = (JViewport)parent;
		Rectangle rect = view.getViewRect();
		
		int horizontal = 0;
		int vertical = 0;
		int verticalDiffTop = cursorLocation.y - rect.y;
		int verticalDiffBottom = rect.height - verticalDiffTop;
		int horizontalDiffLeft = cursorLocation.x - rect.x;
		int horizontalDiffRight = rect.width - horizontalDiffLeft;
		if(verticalDiffTop < DELTA)
			vertical = -1;
		else if(verticalDiffBottom < DELTA)
			vertical = 1;
		if(horizontalDiffLeft < DELTA)
			horizontal = -1;
		else if(horizontalDiffRight < DELTA)
			horizontal = 1;
		
		if(comp instanceof Scrollable)
		{
			Scrollable scrollable = (Scrollable)comp;
			vertical *= scrollable.getScrollableUnitIncrement(rect, SwingConstants.VERTICAL, vertical);
			horizontal *= scrollable.getScrollableUnitIncrement(rect, SwingConstants.HORIZONTAL, horizontal);
		}
		else
		{
			vertical *= DEFAULT_INCREMENT;
			horizontal *= DEFAULT_INCREMENT;
		}

		Dimension viewSize = view.getViewSize();
		Point newPosition = new Point(rect.x + horizontal, rect.y + vertical);
		if(newPosition.x < 0)
			newPosition.x = 0;
		else if(newPosition.x > viewSize.width - rect.width)
			newPosition.x = viewSize.width - rect.width;
		if(newPosition.y < 0)
			newPosition.y = 0;
		else if(newPosition.y > viewSize.height - rect.height)
			newPosition.y = viewSize.height - rect.height;

		if(newPosition.x != rect.x || newPosition.y != rect.y)
		{
			cursorLocation.x += (newPosition.x - rect.x);
			cursorLocation.y += (newPosition.y - rect.y);
			view.setViewPosition(newPosition);
		}
	}
}
