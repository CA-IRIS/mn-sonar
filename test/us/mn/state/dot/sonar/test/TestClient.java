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
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sonar.Checker;
import us.mn.state.dot.sonar.Connection;
import us.mn.state.dot.sonar.Role;
import us.mn.state.dot.sonar.TestObj;
import us.mn.state.dot.sonar.User;
import us.mn.state.dot.sonar.client.Client;
import us.mn.state.dot.sonar.client.ProxyListener;
import us.mn.state.dot.sonar.client.TypeCache;

public class TestClient extends Client {

	static protected final ExceptionHandler HANDLER =
		new ExceptionHandler()
	{
		public boolean handle(Exception e) {
			System.err.println("SHOW: " + e.getMessage());
			System.exit(1);
			return true;
		}
	};

	static protected Properties createProperties() {
		Properties p = new Properties();
		p.setProperty("keystore.file", "/sonar-test.keystore");
		p.setProperty("keystore.password", "sonar-test");
		p.setProperty("sonar.host", "151.111.8.101");
		p.setProperty("sonar.port", "1037");
		return p;
	}

	protected final TypeCache<Role> roles;

	protected final TypeCache<User> users;

	protected final TypeCache<Connection> connections;

	protected final TypeCache<TestObj> tests;

	public TestClient() throws Exception {
		super(createProperties(), HANDLER);
		roles = new TypeCache<Role>(Role.class, this);
		users = new TypeCache<User>(User.class, this);
		connections = new TypeCache<Connection>(Connection.class, this);
		tests = new TypeCache<TestObj>(TestObj.class, this);
		login("username", "password");
		populate(roles);
		populate(users);
		populate(connections);
		populate(tests, true);
	}

	void createProxyListener() {
		roles.addProxyListener(new ProxyListener<Role>() {
			public void proxyAdded(Role proxy) {
				System.err.println("ROLE " + proxy.getName() +
					": " + proxy.getPattern());
			}
			public void enumerationComplete() {
				System.err.println("All roles enumerated");
			}
			public void proxyRemoved(Role proxy) {
				System.err.println("role removed: " +
					proxy.getName());
			}
			public void proxyChanged(Role proxy, String a) {
				System.err.println("role changed: " +
					proxy.getName() + ", " + a);
			}
		});
	}

	void printRoles() {
		roles.findObject(new Checker<Role>() {
			public boolean check(Role r) {
				System.err.println("ROLE " + r.getName() +
					": " + r.getPattern());
				return false;
			}
		});
	}

	void printUsers() {
		users.findObject(new Checker<User>() {
			public boolean check(User u) {
				System.err.println(u.getName() + ": " +
					u.getDn());
				for(Role r: u.getRoles()) {
					System.err.println("\trole: " +
						r.getName());
				}
				return false;
			}
		});
	}

	void printConnections() {
		connections.findObject(new Checker<Connection>() {
			public boolean check(Connection cx) {
				User u = cx.getUser();
				System.err.println(cx.getName() + ": " +
					u.getName() + " (" + u.getFullName() +
					")");
				return false;
			}
		});
	}
}
