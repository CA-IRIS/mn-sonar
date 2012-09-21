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

import java.util.LinkedList;
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sched.Job;
import us.mn.state.dot.sched.Scheduler;

/**
 * Simple class to authenticate a user with an LDAP server.
 *
 * @author Douglas Lau
 */
public class Authenticator {

	/** Check that a user is enabled */
	static private boolean isUserEnabled(UserImpl u) {
		return u != null && u.getEnabled();
	}

	/** Check that a password is sane */
	static protected boolean isPasswordSane(char[] pwd) {
		return pwd != null && pwd.length > 0;
	}

	/** Bypass provider (for debugging) */
	static protected class BypassProvider implements AuthProvider {
		public boolean authenticate(UserImpl user, char[] pwd) {
			return true;
		}
	}

	/** Create an authentication provider */
	static private AuthProvider createProvider(String url) {
		if(url.equals("bypass_authentication"))
			return new BypassProvider();
		else
			return new LDAPProvider(url);
	}

	/** Authentication thread */
	private final Scheduler auth_sched = new Scheduler("Authenticator",
		new ExceptionHandler()
	{
		public boolean handle(Exception e) {
			System.err.println("SONAR: auth_sched error " +
				e.getMessage());
			e.printStackTrace();
			return true;
		}
	});

	/** Task processor */
	private final TaskProcessor processor;

	/** List of authentication providers */
	private final LinkedList<AuthProvider> providers =
		new LinkedList<AuthProvider>();

	/** Create a new user authenticator */
	public Authenticator(TaskProcessor tp, String urls) {
		processor = tp;
		for(String url: urls.split("[ \t]+"))
			providers.add(createProvider(url));
	}

	/** Authenticate a user connection */
	void authenticate(final ConnectionImpl c, final UserImpl u,
		final String name, final char[] password)
	{
		auth_sched.addJob(new Job() {
			public void perform() {
				doAuthenticate(c, u, name, password);
			}
		});
	}

	/** Perform a user authentication */
	private void doAuthenticate(ConnectionImpl c, UserImpl user,
		String name, char[] pwd)
	{
		try {
			if(authenticate(user, pwd))
				processor.finishLogin(c, user);
			else
				processor.failLogin(c, name);
		}
		finally {
			// Clear password in memory
			for(int i = 0; i < pwd.length; i++)
				pwd[i] = '\0';
		}
	}

	/** Authenticate a user's credentials */
	private boolean authenticate(UserImpl user, char[] pwd) {
		if(isUserEnabled(user) && isPasswordSane(pwd)) {
			for(AuthProvider p: providers) {
				if(p.authenticate(user, pwd))
					return true;
			}
		}
		return false;
	}
}
