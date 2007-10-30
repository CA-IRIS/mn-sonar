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
package us.mn.state.dot.sonar.server;

import us.mn.state.dot.sonar.Role;
import us.mn.state.dot.sonar.User;

/**
 * A user must be authenticated by an LDAP server.
 *
 * @author Douglas Lau
 */
public class UserImpl implements User {

	/** Create a new user */
	static public User create(String name) {
		return new UserImpl(name);
	}

	/** Destroy a user */
	public void destroy() {
		// Subclasses must remove user from backing store
	}

	/** Get the SONAR type name */
	public String getTypeName() {
		return SONAR_TYPE;
	}

	/** User name */
	protected final String name;

	/** Get the SONAR object name */
	public String getName() {
		return name;
	}

	/** Create a new user */
	public UserImpl(String n) {
		name = n;
		dn = "cn=" + name;
	}

	/** Roles the user can assume */
	protected RoleImpl[] roles = new RoleImpl[0];

	/** Set the roles assigned to the user */
	public void setRoles(Role[] r) {
		RoleImpl[] _r = new RoleImpl[r.length];
		for(int i = 0; i < r.length; i++)
			_r[i] = (RoleImpl)r[i];
		roles = _r;
	}

	/** Get the roles assigned to the user */
	public Role[] getRoles() {
		return roles;
	}

	/** LDAP Distinguished Name */
	protected String dn;

	/** Set the LDAP Distinguished Name */
	public void setDn(String d) {
		dn = d;
	}

	/** Get the LDAP Distinguished Name */
	public String getDn() {
		return dn;
	}

	/** Full (display) name */
	protected String fullName;

	/** Set the user's full name */
	public void setFullName(String n) {
		fullName = n;
	}

	/** Get the user's full name */
	public String getFullName() {
		return fullName;
	}

	/** Check if the user can read the specified name */
	public boolean canRead(String name) {
		for(RoleImpl r: roles) {
			if(r.getPrivR() && r.matches(name))
				return true;
		}
		return false;
	}

	/** Check if the user can update the specified name */
	public boolean canUpdate(String name) {
		for(RoleImpl r: roles) {
			if(r.getPrivW() && r.matches(name))
				return true;
		}
		return false;
	}

	/** Check if the user can add the specified name */
	public boolean canAdd(String name) {
		for(RoleImpl r: roles) {
			if(r.getPrivC() && r.matches(name))
				return true;
		}
		return false;
	}

	/** Check if the user can remove the specified name */
	public boolean canRemove(String name) {
		for(RoleImpl r: roles) {
			if(r.getPrivD() && r.matches(name))
				return true;
		}
		return false;
	}
}
