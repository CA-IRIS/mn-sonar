/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2008  Minnesota Department of Transportation
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
package us.mn.state.dot.sonar.client;

import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.Role;
import us.mn.state.dot.sonar.User;

/**
 * User permission checker.
 *
 * @author Douglas Lau
 */
class UserPermission {

	/** Invoke special user permission methods */
	static boolean invokeMethod(User user, String m, Object n)
		throws NamespaceError
	{
		if(n != null) {
			String name = n.toString();
			if(m.equals("canRead"))
				return invokeCanRead(user, name);
			if(m.equals("canUpdate"))
				return invokeCanUpdate(user, name);
			if(m.equals("canAdd"))
				return invokeCanAdd(user, name);
			if(m.equals("canRemove"))
				return invokeCanRemove(user, name);
		}
		throw NamespaceError.NAME_UNKNOWN;
	}

	/** Check if a user can read the specified name */
	static boolean invokeCanRead(User user, String name) {
		for(Role r: user.getRoles()) {
			if(r.getPrivR() && name.matches(r.getPattern()))
				return true;
		}
		return false;
	}

	/** Check if a user can update the specified name */
	static boolean invokeCanUpdate(User user, String name) {
		for(Role r: user.getRoles()) {
			if(r.getPrivW() && name.matches(r.getPattern()))
				return true;
		}
		return false;
	}

	/** Check if a user can add the specified name */
	static boolean invokeCanAdd(User user, String name) {
		for(Role r: user.getRoles()) {
			if(r.getPrivC() && name.matches(r.getPattern()))
				return true;
		}
		return false;
	}

	/** Check if a user can remove the specified name */
	static boolean invokeCanRemove(User user, String name) {
		for(Role r: user.getRoles()) {
			if(r.getPrivD() && name.matches(r.getPattern()))
				return true;
		}
		return false;
	}
}
