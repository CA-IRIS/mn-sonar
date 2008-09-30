/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2008  Minnesota Department of Transportation
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

import java.util.Map;
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sonar.Connection;
import us.mn.state.dot.sonar.PropertyLoader;
import us.mn.state.dot.sonar.Role;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.User;
import us.mn.state.dot.sonar.client.Client;
import us.mn.state.dot.sonar.client.ProxyListener;
import us.mn.state.dot.sonar.client.TypeCache;
import us.mn.state.dot.sonar.server.Namespace;
import us.mn.state.dot.sonar.server.RoleImpl;
import us.mn.state.dot.sonar.server.Server;
import us.mn.state.dot.sonar.server.UserImpl;

public class Main {

	static protected final String PROP_LOC = "/sonar-client.properties";

	static protected void printRoles(Map<String, Role> roles) {
		synchronized(roles) {
			for(Role r: roles.values()) {
				System.err.println("ROLE " + r.getName() +
					": " + r.getPattern());
			}
			System.err.println("role count: " + roles.size());
		}
	}

	static protected void printUsers(Map<String, User> users) {
		synchronized(users) {
			for(User u: users.values()) {
				System.err.println(u.getName() + ": " +
					u.getDn());
				for(Role r: u.getRoles()) {
					System.err.println("\trole: " +
						r.getName());
				}
			}
			System.err.println("user count: " + users.size());
		}
	}

	static protected void printConnections(Map<String, Connection> conn) {
		synchronized(conn) {
			for(Connection cx: conn.values()) {
				User u = cx.getUser();
				System.err.println(cx.getName() + ": " +
					u.getName() + " (" + u.getFullName() +
					")");
			}
			System.err.println("connection count: " + conn.size());
		}
	}

	static protected User lookupUser(Map<String, User> users, String u) {
		synchronized(users) {
			return users.get(u);
		}
	}

	static protected Role lookupRole(Map<String, Role> roles, String r) {
		synchronized(roles) {
			return roles.get(r);
		}
	}

	static protected void destroyRole(Map<String, Role> roles, String n) {
		synchronized(roles) {
			for(Role r: roles.values()) {
				if(r.getName().equals(n))
					r.destroy();
			}
		}
	}

	static protected void testClient() throws Exception {
		Client c = new Client(PropertyLoader.load(PROP_LOC),
			new ExceptionHandler() {
				public boolean handle(Exception e) {
					System.err.println("SHOW: " +
						e.getMessage());
					return true;
				}
			}
		);
		c.login("rtmcdatasync", "datasync");
		TypeCache<Role> rc = new TypeCache<Role>(Role.class, c);
		rc.addProxyListener(new ProxyListener<Role>() {
			public void proxyAdded(Role proxy) {
				System.err.println("ROLE " + proxy.getName() +
					": " + proxy.getPattern());
			}
			public void enumerationComplete() {
				System.err.println("All roles enumerated");
			}
			public void proxyRemoved(Role proxy) {
System.err.println("role proxy removed: " + proxy.getName());
			}
			public void proxyChanged(Role proxy, String a) {
//System.err.println("role proxy changed: " + proxy.getName() + ", " + a);
			}
		});
		Map<String, Role> roles = rc.getAll();
		c.populate(rc);
		TypeCache<User> uc = new TypeCache<User>(User.class, c);
		Map<String, User> users = uc.getAll();
		c.populate(uc);
		TypeCache<Connection> cc = new TypeCache<Connection>(
			Connection.class, c);
		Map<String, Connection> conn = cc.getAll();
		c.populate(cc);
		Thread.sleep(2000);
//		rc.createObject("tiger");
//		User u = lookupUser(users, "lau1dou");
//		u.setDn("lau1dou@ad.dot.state.mn.us");
/*		u.setRoles(new Role[] {
			lookupRole(roles, "admin"),
			lookupRole(roles, "view"),
		});
		Thread.sleep(1000); */
		printRoles(roles);
		printUsers(users);
		printConnections(conn);
		c.join();
	}

	static protected Namespace createNamespace() throws SonarException {
		Namespace n = new Namespace();
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
		Namespace n = createNamespace();
		Server s = new Server(n);
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
			if(checkClient(args))
				testClient();
			else
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
