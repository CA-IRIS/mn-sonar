/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2006-2008  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.sonar;

import java.nio.channels.CancelledKeyException;
import java.util.LinkedList;
import javax.net.ssl.SSLException;

/**
 * Thread which processes tasks on server namespace.
 *
 * @author Douglas Lau
 */
public class TaskProcessor extends Thread {

	/** List of tasks to perform */
	protected final LinkedList<Task> todo = new LinkedList<Task>();

	/** Create a new task processor */
	public TaskProcessor() {
		super("Task Processor");
		setDaemon(true);
		start();
	}

	/** Get the next task on the "todo" list */
	protected synchronized Task next() {
		while(todo.isEmpty()) {
			try {
				wait();
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		return todo.remove();
	}

	/** Do the current task */
	protected void doTask(Task t) {
		try {
			t.perform();
		}
		catch(CancelledKeyException e) {
			System.err.println("SONAR: Key already cancelled");
		}
		catch(SSLException e) {
			System.err.println("SONAR: SSL error " +e.getMessage());
		}
		catch(Exception e) {
			System.err.println("SONAR: error " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** Process all tasks */
	public void run() {
		Task t = next();
		while(!isInterrupted()) {
			doTask(t);
			t = next();
		}
	}

	/** Add a task for this thread to perform */
	public synchronized void add(Task t) {
		todo.add(t);
		notify();
	}
}
