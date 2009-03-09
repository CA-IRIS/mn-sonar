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

import us.mn.state.dot.sonar.PropertyLoader;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.server.ServerNamespace;
import us.mn.state.dot.sonar.server.RoleImpl;
import us.mn.state.dot.sonar.server.Server;
import us.mn.state.dot.sonar.server.UserImpl;

public class Main {

	static protected final String PROP_FILE = "/etc/sonar/sonar.properties";

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
		UserImpl u = new UserImpl("rtmcdatasync");
		u.setDn("RTMC DataSync");
		u.setRoles(new RoleImpl[] { r });
		u.setFullName("RTMC DataSync");
		n.add(u);
		n.add(new TestImpl("name_A", 10));
		n.add(new TestImpl("name_B", 20));
		for(int i = 0; i < 5000; i++)
			n.add(new TestImpl("name_" + i, i));
		return n;
	}

	static protected void testServer() throws Exception {
		ServerNamespace n = createNamespace();
		Server s = new Server(n, PropertyLoader.load(PROP_FILE));
		s.join();
	}

	static protected boolean checkClient(String[] args) {
		for(String a: args) {
			if(a.equals("-c") || a.equals("--client"))
				return true;
		}
		return false;
	}

	/** Test SONAR server */
	static public void main(String[] args) {
		try {
			if(checkClient(args)) {
				TestClient c = new TestClient();
				c.printRoles();
				c.printUsers();
				c.printConnections();
				c.join();
			} else
				testServer();
		}
		catch(SonarException e) {
			System.err.println("SONAR " + e.getMessage());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
