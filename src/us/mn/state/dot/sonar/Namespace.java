/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2013  Minnesota Department of Transportation
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
		Field f = (Field)t.getField("SONAR_TYPE");
		return (String)f.get(t);
	}

	/** Get names of possible SONAR types */
	static public String[] typeNames(Class t)
		throws NoSuchFieldException, IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(t);
		Field f = (Field)t.getField("SONAR_TYPES");
		return (String [])f.get(t);
	}

	/** Make an array of the given class and size */
	static private Object[] makeArray(Class t, int size) {
		return (Object [])Array.newInstance(t, size);
	}

	/** Marshall a java object into a parameter value string */
	public String marshall(Object v) {
		if(v instanceof SonarObject) {
			SonarObject o = (SonarObject)v;
			return o.getName();
		} else if(v != null)
			return v.toString();
		else
			return NULL_STR;
	}

	/** Marshall java parameters into a parameter value string */
	public String[] marshall(Class t, Object[] v) {
		if(t.isArray())
			v = (Object [])v[0];
		String[] values = new String[v.length];
		for(int i = 0; i < v.length; i++)
			values[i] = marshall(v[i]);
		return values;
	}

	/** Unmarshall a parameter value string into a java object */
	public Object unmarshall(Class t, String p) throws ProtocolError {
		if(NULL_STR.equals(p))
			return null;
		if(t == String.class)
			return p;
		try {
			if(t == Integer.TYPE || t == Integer.class)
				return Integer.valueOf(p);
			else if(t == Short.TYPE || t == Short.class)
				return Short.valueOf(p);
			else if(t == Boolean.TYPE || t == Boolean.class)
				return Boolean.valueOf(p);
			else if(t == Float.TYPE || t == Float.class)
				return Float.valueOf(p);
			else if(t == Long.TYPE || t == Long.class)
				return Long.valueOf(p);
			else if(t == Double.TYPE || t == Double.class)
				return Double.valueOf(p);
		}
		catch(NumberFormatException e) {
			throw ProtocolError.INVALID_PARAMETER;
		}
		if(SonarObject.class.isAssignableFrom(t))
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
		catch(NoSuchFieldException e) {
			System.err.println("SONAR: SONAR_TYPE and " +
				"SONAR_TYPES not defined for " + t);
			throw ProtocolError.INVALID_PARAMETER;
		}
		catch(Exception e) {
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
		catch(NoSuchFieldException e) {
			for(String typ: typeNames(t)) {
				Object o = lookupObject(typ, p);
				if(o != null)
					return o;
			}
			return null;
		}
	}

	/** Unmarshall parameter strings into one java parameter */
	public Object unmarshall(Class t, String[] v) throws ProtocolError {
		if(t.isArray())
			return unmarshallArray(t.getComponentType(), v);
		else {
			if(v.length != 1)
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			return unmarshall(t, v[0]);
		}
	}

	/** Unmarshall parameter strings into a java array parameter */
	private Object[] unmarshallArray(Class t, String[] v)
		throws ProtocolError
	{
		Object[] values = makeArray(t, v.length);
		for(int i = 0; i < v.length; i++)
			values[i] = unmarshall(t, v[i]);
		return values;
	}

	/** Unmarshall multiple parameters */
	public Object[] unmarshall(Class[] pt, String[] v) throws ProtocolError
	{
		if(pt.length == 1 && pt[0].isArray()) {
			return new Object[] {
				unmarshall(pt[0], v)
			};
		}
		if(pt.length != v.length)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		Object[] params = new Object[pt.length];
		for(int i = 0; i < params.length; i++)
			params[i] = unmarshall(pt[i], v[i]);
		return params;
	}

	/** Check if a user has read privileges for a name */
	public boolean canRead(User u, Name n) {
		return u.getEnabled() && canRead(u.getRole(), n);
	}

	/** Check if a role has read privileges for a name */
	private boolean canRead(Role r, Name n) {
		return r != null && r.getEnabled() &&
		       canRead(r.getCapabilities(), n);
	}

	/** Check if a set of capabilites has read privileges for a name */
	private boolean canRead(Capability[] caps, Name n) {
		for(Capability c: caps) {
			if(c.getEnabled() && canRead(c, n))
				return true;
		}
		return false;
	}

	/** Check if a capability has read privileges for a name.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be read according to capability. */
	private boolean canRead(Capability c, Name n) {
		Iterator<SonarObject> it = iterator(Privilege.SONAR_TYPE);
		while(it.hasNext()) {
			SonarObject so = it.next();
			if(so instanceof Privilege) {
				if(canRead((Privilege)so, c, n))
					return true;
			}
		}
		return false;
	}

	/** Check if a privilege/capability has read privileges for a name.
	 * @param p Privilege to check.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be read according to priv/cap. */
	private boolean canRead(Privilege p, Capability c, Name n) {
		return p.getCapability() == c && p.getPrivR() && n.matches(p);
	}

	/** Check if a user has update privileges for a name */
	public boolean canUpdate(User u, Name n) {
		return u.getEnabled() && canUpdate(u.getRole(), n);
	}

	/** Check if a role has update privileges for a name */
	private boolean canUpdate(Role r, Name n) {
		return r != null && r.getEnabled() &&
		       canUpdate(r.getCapabilities(), n);
	}

	/** Check if a set of capabilites has update privileges for a name */
	private boolean canUpdate(Capability[] caps, Name n) {
		for(Capability c: caps) {
			if(c.getEnabled() && canUpdate(c, n))
				return true;
		}
		return false;
	}

	/** Check if a capability has update privileges for a name.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be updated according to capability. */
	private boolean canUpdate(Capability c, Name n) {
		Iterator<SonarObject> it = iterator(Privilege.SONAR_TYPE);
		while(it.hasNext()) {
			SonarObject so = it.next();
			if(so instanceof Privilege) {
				if(canUpdate((Privilege)so, c, n))
					return true;
			}
		}
		return false;
	}

	/** Check if a privilege/capability has update privileges for a name.
	 * @param p Privilege to check.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be updated according to priv/cap. */
	private boolean canUpdate(Privilege p, Capability c, Name n) {
		return p.getCapability() == c && p.getPrivW() && n.matches(p);
	}

	/** Check if a user has add privileges for a name */
	public boolean canAdd(User u, Name n) {
		return u.getEnabled() && canAdd(u.getRole(), n);
	}

	/** Check if a role has add privileges for a name */
	private boolean canAdd(Role r, Name n) {
		return r != null && r.getEnabled() &&
		       canAdd(r.getCapabilities(), n);
	}

	/** Check if a set of capabilites has add privileges for a name */
	private boolean canAdd(Capability[] caps, Name n) {
		for(Capability c: caps) {
			if(c.getEnabled() && canAdd(c, n))
				return true;
		}
		return false;
	}

	/** Check if a capability has add privileges for a name.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be added according to capability. */
	private boolean canAdd(Capability c, Name n) {
		Iterator<SonarObject> it = iterator(Privilege.SONAR_TYPE);
		while(it.hasNext()) {
			SonarObject so = it.next();
			if(so instanceof Privilege) {
				if(canAdd((Privilege)so, c, n))
					return true;
			}
		}
		return false;
	}

	/** Check if a privilege/capability has add privileges for a name.
	 * @param p Privilege to check.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be added according to priv/cap. */
	private boolean canAdd(Privilege p, Capability c, Name n) {
		return p.getCapability() == c && p.getPrivC() && n.matches(p);
	}

	/** Check if a user has remove privileges for a name */
	public boolean canRemove(User u, Name n) {
		return u.getEnabled() && canRemove(u.getRole(), n);
	}

	/** Check if a role has remove privileges for a name */
	private boolean canRemove(Role r, Name n) {
		return r != null && r.getEnabled() &&
		       canRemove(r.getCapabilities(), n);
	}

	/** Check if a set of capabilites has remove privileges for a name */
	private boolean canRemove(Capability[] caps, Name n) {
		for(Capability c: caps) {
			if(c.getEnabled() && canRemove(c, n))
				return true;
		}
		return false;
	}

	/** Check if a capability has remove privileges for a name.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be removed according to capability. */
	private boolean canRemove(Capability c, Name n) {
		Iterator<SonarObject> it = iterator(Privilege.SONAR_TYPE);
		while(it.hasNext()) {
			SonarObject so = it.next();
			if(so instanceof Privilege) {
				if(canRemove((Privilege)so, c, n))
					return true;
			}
		}
		return false;
	}

	/** Check if a privilege/capability has remove privileges for a name.
	 * @param p Privilege to check.
	 * @param c Capability to check.
	 * @param n Name to check.
	 * @return true If name can be removed according to priv/cap. */
	private boolean canRemove(Privilege p, Capability c, Name n) {
		return p.getCapability() == c && p.getPrivD() && n.matches(p);
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
