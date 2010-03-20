/*
 *  eXist's  Gate extension - REST client for automate document management
 *  form any browser in any desktop application on any client platform
 *  Copyright (C) 2010,  Evgeny V. Gazdovsky (gazdovsky@gmail.com)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class TaskManager extends TimerTask{
	
	List <Task> tasks = new ArrayList<Task>();
	
	boolean haveNewTask = false; 
	
	public void addTask(Task task){
		tasks.add(task);
		haveNewTask = true;
	}
	
	public void run(){
		if (haveNewTask){
			haveNewTask = false;
			tasks.get(tasks.size()-1).execute();
		}
	}
	
}
