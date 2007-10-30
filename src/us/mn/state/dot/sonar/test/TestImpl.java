/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2007  Minnesota Department of Transportation
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
package us.mn.state.dot.sonar.test;

public class TestImpl implements Test {

	public String getTypeName() {
		return SONAR_TYPE;
	}

	protected final String name;

	public TestImpl(String n) {
		name = n;
	}

	public TestImpl(String n, int l) {
		name = n;
		location = l;
	}

	public String getName() {
		return name;
	}

	protected int location;

	public int getLocation() {
		return location;
	}

	public void setLocation(int l) {
		// overridden by doSetLocation
	}

	public void doSetLocation(int l) throws Exception {
		throw new Exception("crazy exception");
	}

	protected String notes = "some_notes";

	public String getNotes() {
		return notes;
	}

	public void setNotes(String n) {
		notes = n;
	}

	static public Test create(String name) {
		return new TestImpl(name);
	}

	public void destroy() {
System.err.println("TestImpl.destroy(): " + name);
	}
}
