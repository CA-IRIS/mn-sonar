/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2010  Minnesota Department of Transportation
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
 * A user must be authenticated by an LDAP server.
 *
 * @author Douglas Lau
 */
public interface User extends SonarObject {

	/** SONAR type name */
	String SONAR_TYPE = "user";

	/** Set the roles assigned to the user */
	void setRoles(Role[] r);

	/** Get the roles assigned to the user */
	Role[] getRoles();

	/** Set the LDAP Distinguished Name */
	void setDn(String d);

	/** Get the LDAP Distinguished Name */
	String getDn();

	/** Set the user's full name */
	void setFullName(String n);

	/** Get the user's full name */
	String getFullName();

	/** Set the active flag */
	void setActive(boolean a);

	/** Get the active flag */
	boolean getActive();
}
