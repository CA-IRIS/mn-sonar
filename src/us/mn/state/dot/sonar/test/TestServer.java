/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2009  Minnesota Department of Transportation
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

import java.util.Properties;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.server.ServerNamespace;
import us.mn.state.dot.sonar.server.RoleImpl;
import us.mn.state.dot.sonar.server.Server;
import us.mn.state.dot.sonar.server.UserImpl;

public class TestServer extends Server {

	static protected Properties createProperties() {
		Properties p = new Properties();
		p.setProperty("keystore.file", "/sonar-test.keystore");
		p.setProperty("keystore.password", "sonar-test");
		p.setProperty("sonar.ldap.urls", "ldaps://localhost:636");
		p.setProperty("sonar.port", "1037");
		return p;
	}

	static protected ServerNamespace createNamespace()
		throws SonarException
	{
		ServerNamespace n = new ServerNamespace();
		RoleImpl r = new RoleImpl("admin");
		r.setPattern(".*");
		r.setPrivR(true);
		r.setPrivW(true);
		r.setPrivC(true);
		r.setPrivD(true);
		n.add(r);
		UserImpl u = new UserImpl("username");
		u.setDn("Test user");
		u.setRoles(new RoleImpl[] { r });
		u.setFullName("Test user");
		n.add(u);
		n.add(new TestImpl("name_A", 10));
		n.add(new TestImpl("name_B", 20));
		for(int i = 0; i < 5000; i++)
			n.add(new TestImpl("name_" + i, i));
		return n;
	}

	public TestServer() throws Exception {
		super(createNamespace(), createProperties());
	}
}
