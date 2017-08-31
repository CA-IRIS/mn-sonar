/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2016  Minnesota Department of Transportation
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
package us.mn.state.dot.sonar;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Iterator;

/**
 * A namespace is a mapping of names to objects.
 *
 * @author Douglas Lau
 */
abstract public class Namespace {

	/** NULL REF string */
	static private String NULL_STR = String.valueOf(Message.NULL_REF.code);

	/** Get the name of a SONAR type */
	static public String typeName(Class t)
		throws NoSuchFieldException, IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(t);
		Field f = (Field) t.getField("SONAR_TYPE");
		return (String) f.get(t);
	}

	/** Get names of possible SONAR types */
	static public String[] typeNames(Class t)
		throws NoSuchFieldException, IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(t);
		Field f = (Field) t.getField("SONAR_TYPES");
		return (String []) f.get(t);
	}

	/** Make an array of the given class and size */
	static private Object[] makeArray(Class t, int size) {
		return (Object []) Array.newInstance(t, size);
	}

	/** Marshall a java object into a parameter value string */
	public String marshall(Object v) {
		if (v instanceof SonarObject) {
			SonarObject o = (SonarObject) v;
			return o.getName();
		} else if (v != null)
			return v.toString();
		else
			return NULL_STR;
	}

	/** Marshall java parameters into a parameter value string */
	public String[] marshall(Class t, Object[] v) {
		if (t.isArray())
			v = (Object [])v[0];
		String[] values = new String[v.length];
		for (int i = 0; i < v.length; i++)
			values[i] = marshall(v[i]);
		return values;
	}

	/** Unmarshall a parameter value string into a java object */
	public Object unmarshall(Class t, String p) throws ProtocolError {
		if (NULL_STR.equals(p))
			return null;
		if (t == String.class)
			return p;
		try {
			if (t == Integer.TYPE || t == Integer.class)
				return Integer.valueOf(p);
			else if (t == Short.TYPE || t == Short.class)
				return Short.valueOf(p);
			else if (t == Boolean.TYPE || t == Boolean.class)
				return Boolean.valueOf(p);
			else if (t == Float.TYPE || t == Float.class)
				return Float.valueOf(p);
			else if (t == Long.TYPE || t == Long.class)
				return Long.valueOf(p);
			else if (t == Double.TYPE || t == Double.class)
				return Double.valueOf(p);
		}
		catch (NumberFormatException e) {
			throw ProtocolError.INVALID_PARAMETER;
		}
		if (SonarObject.class.isAssignableFrom(t))
			return unmarshallObject(t, p);
		else
			throw ProtocolError.INVALID_PARAMETER;
	}

	/** Unmarshall a SONAR object reference */
	private Object unmarshallObject(Class t, String p)
		throws ProtocolError
	{
		try {
			return unmarshallObjectB(t, p);
		}
		catch (NoSuchFieldException e) {
			System.err.println("SONAR: SONAR_TYPE and " +
				"SONAR_TYPES not defined for " + t);
			throw ProtocolError.INVALID_PARAMETER;
		}
		catch (Exception e) {
			System.err.println("SONAR: unmarshall \"" + p +
				"\": " + e.getMessage());
			throw ProtocolError.INVALID_PARAMETER;
		}
	}

	/** Unmarshall a SONAR object reference */
	private Object unmarshallObjectB(Class t, String p)
		throws NoSuchFieldException, IllegalAccessException
	{
		try {
			return lookupObject(typeName(t), p);
		}
		catch (NoSuchFieldException e) {
			for (String typ: typeNames(t)) {
				Object o = lookupObject(typ, p);
				if (o != null)
					return o;
			}
			return null;
		}
	}

	/** Unmarshall parameter strings into one java parameter */
	public Object unmarshall(Class t, String[] v) throws ProtocolError {
		if (t.isArray())
			return unmarshallArray(t.getComponentType(), v);
		else {
			if (v.length != 1)
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			return unmarshall(t, v[0]);
		}
	}

	/** Unmarshall parameter strings into a java array parameter */
	private Object[] unmarshallArray(Class t, String[] v)
		throws ProtocolError
	{
		Object[] values = makeArray(t, v.length);
		for (int i = 0; i < v.length; i++)
			values[i] = unmarshall(t, v[i]);
		return values;
	}

	/** Unmarshall multiple parameters */
	public Object[] unmarshall(Class[] pt, String[] v) throws ProtocolError
	{
		if (pt.length == 1 && pt[0].isArray()) {
			return new Object[] {
				unmarshall(pt[0], v)
			};
		}
		if (pt.length != v.length)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		Object[] params = new Object[pt.length];
		for (int i = 0; i < params.length; i++)
			params[i] = unmarshall(pt[i], v[i]);
		return params;
	}

	/** Check if a user has read privileges.
	 * @param pc Privilege checker.
	 * @param u User to check.
	 * @return true If user has read privileges. */
	public boolean canRead(final PrivChecker pc, User u) {
		return u.getEnabled() && checkPriv(new PrivChecker() {
			public boolean check(Privilege p) {
				return !p.getWrite() && pc.check(p);
			}
		}, u.getRole());
	}

	/** Check if a user has write privileges.
	 * @param pc Privilege checker.
	 * @param u User to check.
	 * @return true If user has write privileges. */
	public boolean canWrite(final PrivChecker pc, User u) {
		return u.getEnabled() && checkPriv(new PrivChecker() {
			public boolean check(Privilege p) {
				return p.getWrite() && pc.check(p);
			}
		}, u.getRole());
	}

	/** Check if a role has privileges */
	private boolean checkPriv(PrivChecker pc, Role r) {
		return r != null
		    && r.getEnabled()
		    && checkPriv(pc, r.getCapabilities());
	}

	/** Check if a set of capabilites has privileges */
	private boolean checkPriv(PrivChecker pc, Capability[] caps) {
		for (Capability c: caps) {
			if (c.getEnabled() && checkPriv(pc, c))
				return true;
		}
		return false;
	}

	/** Check if a capability has privileges.
	 * @param pc Privilege checker.
	 * @param c Capability to check.
	 * @return true If capability has privileges. */
	private boolean checkPriv(PrivChecker pc, Capability c) {
		Iterator<SonarObject> it = iterator(Privilege.SONAR_TYPE);
		while (it.hasNext()) {
			SonarObject so = it.next();
			if (so instanceof Privilege) {
				Privilege p = (Privilege) so;
				if ((p.getCapability() == c) && pc.check(p))
					return true;
			}
		}
		return false;
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name
	 * @param oname Sonar object name
	 * @return Object from namespace or null if name does not exist */
	abstract public SonarObject lookupObject(String tname, String oname);

	/** Get an iterator for all objects of a type.
	 * @param tname Sonar type name.
	 * @return Iterator of all objects of the type. */
	abstract public Iterator<SonarObject> iterator(String tname);

	/** Get a count of the number of objects of the specified type.
	 * @param tname Sonar type name
	 * @return Total number of objects of the specified type */
	abstract public int getCount(String tname);
}
