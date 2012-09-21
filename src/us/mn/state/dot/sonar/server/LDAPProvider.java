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

import java.util.Hashtable;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;

/**
 * LDAP user authentication provider.
 *
 * @author Douglas Lau
 */
public class LDAPProvider implements AuthProvider {

	/** Check that a DN is sane */
	static private boolean isDnSane(String dn) {
		return dn != null && dn.length() > 0;
	}

	/** Environment for creating a directory context */
	private final Hashtable<String, Object> env =
		new Hashtable<String, Object>();

	/** Create a new LDAP authentication provider */
	public LDAPProvider(String url) {
		env.put(Context.INITIAL_CONTEXT_FACTORY,
			"com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, url);
		env.put("com.sun.jndi.ldap.connect.timeout", "5000");
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
	public void authenticate(UserImpl user, char[] pwd)
		throws AuthenticationException, NamingException
	{
		String dn = user.getDn();
		if(!isDnSane(dn))
			throw new AuthenticationException("Invalid dn");
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
