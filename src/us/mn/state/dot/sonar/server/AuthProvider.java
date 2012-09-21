/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2006-2012  Minnesota Department of Transportation
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

import javax.naming.AuthenticationException;
import javax.naming.NamingException;

/**
 * User authentication provider interface.
 *
 * @author Douglas Lau
 */
public interface AuthProvider {

	/** Authenticate a user.
	 * @param user User to be authenticated.
	 * @param pwd Password to check for user.
	 * @throws AuthenticationException if user/password could not be
	 *                                 authenticated.
	 * @throws NamingException if there was some other error. */
	void authenticate(UserImpl user, char[] pwd)
		throws AuthenticationException, NamingException;
}
