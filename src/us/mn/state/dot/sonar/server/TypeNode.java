/*
 * SONAR -- Simple Object Notification And Replication
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

import java.util.HashMap;
import us.mn.state.dot.sonar.Checker;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.MessageEncoder;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * A type node represents the first-level nodes in the SONAR namespace. It
 * contains all information about a SONAR type.
 *
 * @author Douglas Lau
 */
public class TypeNode {

	/** Type name */
	public final String name;

	/** All child objects of this type are put here */
	private final HashMap<String, SonarObject> children =
		new HashMap<String, SonarObject>();

	/** An attribute dispatcher can set and get attributes on objects */
	private final AttributeDispatcher dispatcher;

	/** Create a namespace type node */
	public TypeNode(String n, Class c) {
		name = n;
		dispatcher = new AttributeDispatcher(c);
	}

	/** Add an object to the type node */
	public void add(SonarObject o) throws NamespaceError {
		String name = o.getName();
		synchronized(children) {
			if(children.containsKey(name))
				throw NamespaceError.NAME_EXISTS;
			else
				children.put(name, o);
		}
	}

	/** Create a new object in the type node */
	public SonarObject createObject(String name) throws SonarException {
		synchronized(children) {
			if(children.containsKey(name))
				throw NamespaceError.NAME_EXISTS;
		}
		return dispatcher.createObject(name);
	}

	/** Store an object in the type node */
	public void storeObject(SonarObject o) throws SonarException {
		String name = o.getName();
		synchronized(children) {
			if(children.containsKey(name))
				throw NamespaceError.NAME_EXISTS;
			dispatcher.storeObject(o);
			children.put(name, o);
		}
	}

	/** Remove an object from the type node */
	public SonarObject removeObject(SonarObject o) throws SonarException {
		String n = o.getName();
		SonarObject object;
		synchronized(children) {
			object = children.remove(n);
			if(object == null)
				throw NamespaceError.NAME_UNKNOWN;
			if(object != o)
				throw NamespaceError.NAME_EXISTS;
			try {
				dispatcher.destroyObject(o);
			}
			catch(SonarException e) {
				children.put(n, o);
				throw e;
			}
		}
		return object;
	}

	/** Lookup an object from the given name */
	protected SonarObject _lookupObject(String n) {
		synchronized(children) {
			return children.get(n);
		}
	}

	/** Lookup an object from the given name */
	public SonarObject lookupObject(String n) throws NamespaceError {
		SonarObject o = _lookupObject(n);
		if(o == null)
			throw NamespaceError.NAME_UNKNOWN;
		else
			return o;
	}

	/** Get the type attributes */
	public String[] getAttributes() {
		return dispatcher.attributes;
	}

	/** Get the value of an attribute */
	public String[] getValue(SonarObject o, String a)
		throws SonarException
	{
		return dispatcher.getValue(o, a);
	}

	/** Enumerate all attributes of the named object */
	public void enumerateObject(MessageEncoder enc, SonarObject o)
		throws SonarException
	{
		assert(o.getTypeName() == name);
		boolean first = true;
		for(String a: getAttributes()) {
			String[] v = getValue(o, a);
			if(first) {
				a = Names.makePath(o, a);
				first = false;
			}
			enc.encode(Message.ATTRIBUTE, a, v);
		}
		if(first)
			enc.encode(Message.TYPE, name);
		enc.encode(Message.OBJECT, o.getName());
	}

	/** Enumerate all the objects of the type node */
	public void enumerateObjects(MessageEncoder enc) throws SonarException {
		synchronized(children) {
			for(SonarObject o: children.values())
				enumerateObject(enc, o);
		}
	}

	/** Set the value of an attribute */
	public SonarObject setValue(String oname, String a, String[] v)
		throws SonarException
	{
		synchronized(children) {
			SonarObject o = children.get(oname);
			if(o != null) {
				dispatcher.setValue(o, a, v);
				return null;
			} else {
				o = dispatcher.createObject(oname);
				setField(o, a, v);
				return o;
			}
		}
	}

	/** Set the field attribute value */
	public void setField(SonarObject o, String a, String[] v)
		throws SonarException
	{
		dispatcher.setField(o, a, v);
	}

	/** Find an object using the supplied checker callback */
	public SonarObject find(Checker c) {
		synchronized(children) {
			for(SonarObject o: children.values()) {
				if(c.check(o))
					return o;
			}
		}
		return null;
	}
}
