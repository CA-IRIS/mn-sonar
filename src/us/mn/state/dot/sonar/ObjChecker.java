/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2017  Minnesota Department of Transportation
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

/**
 * Interface for checking privilege.
 *
 * @author Douglas Lau
 */
public class ObjChecker implements PrivChecker {

	/** Object to check */
	private final SonarObject obj;

	/** Attribute name */
	private final String attr;

	/** Create a new object checker */
	public ObjChecker(SonarObject so) {
		this(so, null);
	}

	/** Create a new object checker */
	public ObjChecker(SonarObject so, String a) {
		obj = so;
		attr = a;
	}

	/** Get type part */
	@Override
	public String getTypePart() {
		return obj.getTypeName();
	}

	/** Check for read privilege.
	 * @param p Privilege to check. */
	@Override
	public boolean checkRead(Privilege p) {
		return p.getTypeN().equals(obj.getTypeName());
	}

	/** Check for write privilege.
	 * @param p Privilege to check. */
	@Override
	public boolean checkWrite(Privilege p) {
		// NOTE: object, group and attribute checks are
		//       only valid for write privileges.
		return p.getTypeN().equals(obj.getTypeName())
		    && checkObj(p)
		    && checkGroup(p)
		    && checkAttr(p);
	}

	/** Check if the object matches a privilege */
	private boolean checkObj(Privilege p) {
		String o = p.getObjN();
		return "".equals(o) || obj.getName().matches(o);
	}

	/** Check if the object matches a privilege group */
	private boolean checkGroup(Privilege p) {
		String g = p.getGroupN();
		return "".equals(g) || obj.isInGroup(g);
	}

	/** Check if an attribute matches a privilege */
	private boolean checkAttr(Privilege p) {
		return (null == attr) || checkAttr(p.getAttrN());
	}

	/** Check if an attribute matches */
	private boolean checkAttr(String a) {
		return "".equals(a) || attr.equals(a);
	}
}
