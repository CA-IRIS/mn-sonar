/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2006-2011  Minnesota Department of Transportation
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

import java.util.Hashtable;
import java.util.LinkedList;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import us.mn.state.dot.sched.TimeSteward;

/**
 * Simple class to authenticate a user with an LDAP server.
 *
 * @author Douglas Lau
 */
public class LDAPAuthenticator {

	/** Check that a DN is sane */
	static protected boolean isDnSane(String dn) {
		return dn != null && dn.length() > 0;
	}

	/** Check that a password is sane */
	static protected boolean isPasswordSane(char[] pwd) {
		return pwd != null && pwd.length > 0;
	}

	/** LDAP provider */
	protected class Provider {

		/** Environment for creating a directory context */
		protected final Hashtable<String, Object> env =
			new Hashtable<String, Object>();

		/** Create a new LDAP provider */
		protected Provider(String url) {
			env.put(Context.INITIAL_CONTEXT_FACTORY,
				"com.sun.jndi.ldap.LdapCtxFactory");
			env.put(Context.PROVIDER_URL, url);
			if(url.startsWith("ldaps")) {
				env.put(Context.SECURITY_PROTOCOL, "ssl");
				env.put("java.naming.ldap.factory.socket",
					LDAPSocketFactory.class.getName());
			}
		}

		/** Get a string representation of the provider (URL) */
		public String toString() {
			Object url = env.get(Context.PROVIDER_URL);
			if(url != null)
				return url.toString();
			else
				return "";
		}

		/** Authenticate a user's credentials */
		protected void authenticate(String dn, char[] pwd)
			throws AuthenticationException, NamingException
		{
			env.put(Context.SECURITY_PRINCIPAL, dn);
			env.put(Context.SECURITY_CREDENTIALS, pwd);
			try {
				InitialDirContext ctx =
					new InitialDirContext(env);
				ctx.close();
			}
			finally {
				// We shouldn't keep these around
				env.remove(Context.SECURITY_PRINCIPAL);
				env.remove(Context.SECURITY_CREDENTIALS);
			}
		}
	}

	/** List of LDAP providers */
	protected final LinkedList<Provider> providers =
		new LinkedList<Provider>();

	/** Create a new LDAP authenticator */
	public LDAPAuthenticator(String urls) {
		for(String url: urls.split("[ \t]+"))
			providers.add(new Provider(url));
	}

	/** Authenticate a user's credentials */
	public void authenticate(String dn, char[] pwd) throws PermissionDenied
	{
		if(isDnSane(dn) && isPasswordSane(pwd)) {
			for(Provider p: providers) {
				try {
					p.authenticate(dn, pwd);
					return;
				}
				catch(AuthenticationException e) {
					// Try next provider
				}
				catch(NamingException e) {
					System.err.println("SONAR: " +
						namingMessage(e) + " on " + p);
					// Try next provider
				}
			}
		}
		System.err.println("SONAR: LDAP auth failure: " + dn + " @ " +
			TimeSteward.getDateInstance() + ".");
		throw PermissionDenied.AUTHENTICATION_FAILED;
	}

	/** Get a useful message string from a naming exception */
	static protected String namingMessage(NamingException e) {
		Throwable c = e.getCause();
		return (c != null)
			? c.getMessage()
			: e.getClass().getSimpleName();
	}
}
