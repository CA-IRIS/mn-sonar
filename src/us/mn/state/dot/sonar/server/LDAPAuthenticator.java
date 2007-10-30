/*
 * IRIS -- Intelligent Roadway Information System
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

import java.util.Hashtable;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLContext;

/**
 * Simple class to authenticate a user with an LDAP server.
 *
 * @author Douglas Lau
 */
public class LDAPAuthenticator {

	/** Environment for creating a directory context */
	protected final Hashtable<String, Object> env =
		new Hashtable<String, Object>();

	/** Create a new LDAP authenticator */
	public LDAPAuthenticator(String host, int port) {
		env.put(Context.INITIAL_CONTEXT_FACTORY,
			"com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
	}

	/** Create a new LDAP authenticator with SSL */
	public LDAPAuthenticator(SSLContext context, String host, int port) {
		LDAPSocketFactory.FACTORY = context.getSocketFactory();
		env.put(Context.INITIAL_CONTEXT_FACTORY,
			"com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
		env.put(Context.SECURITY_PROTOCOL, "ssl");
		env.put("java.naming.ldap.factory.socket",
			"us.mn.state.dot.sonar.server.LDAPSocketFactory");
	}

	/** Authenticate a user's credentials */
	public void authenticate(String dn, char[] pwd) throws PermissionDenied
	{
		if(pwd == null || pwd.length == 0)
			throw PermissionDenied.AUTHENTICATION_FAILED;
		env.put(Context.SECURITY_PRINCIPAL, dn);
		env.put(Context.SECURITY_CREDENTIALS, pwd);
		try {
			InitialDirContext ctx = new InitialDirContext(env);
			ctx.close();
		}
		catch(AuthenticationException e) {
			throw PermissionDenied.AUTHENTICATION_FAILED;
		}
		catch(NamingException e) {
			e.printStackTrace();
			throw PermissionDenied.AUTHENTICATION_FAILED;
		}
	}
}
