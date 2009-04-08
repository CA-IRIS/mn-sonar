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
package us.mn.state.dot.sonar.server;

import java.util.HashMap;
import us.mn.state.dot.sonar.Checker;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.MessageEncoder;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
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
	public TypeNode(Namespace ns, String n, Class c) {
		name = n;
		dispatcher = new AttributeDispatcher(c, ns);
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

	/** Add an object to the type node without storing */
	public void addObject(SonarObject o) throws NamespaceError {
		String name = o.getName();
		synchronized(children) {
			if(children.containsKey(name))
				throw NamespaceError.NAME_EXISTS;
			else
				children.put(name, o);
		}
	}

	/** Remove an object from the type node */
	public void removeObject(SonarObject o) throws SonarException {
		String n = o.getName();
		synchronized(children) {
			SonarObject obj = children.remove(n);
			if(obj == null)
				throw NamespaceError.nameUnknown(n);
			if(obj != o)
				throw NamespaceError.NAME_EXISTS;
			try {
				dispatcher.destroyObject(o);
			}
			catch(SonarException e) {
				children.put(n, o);
				throw e;
			}
		}
	}

	/** Lookup an object from the given name */
	public SonarObject lookupObject(String n) {
		synchronized(children) {
			return children.get(n);
		}
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
		for(String a: dispatcher.getReadableAttributes()) {
			String[] v = getValue(o, a);
			if(first) {
				a = new Name(o, a).toString();
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

	/** Enumerate an attribute for all objects of the type node */
	public void enumerateAttribute(MessageEncoder enc, String aname)
		throws SonarException
	{
		synchronized(children) {
			for(SonarObject o: children.values()) {
				String a = new Name(o, aname).toString();
				String[] v = dispatcher.getValue(o, aname);
				enc.encode(Message.ATTRIBUTE, a, v);
			}
		}
	}

	/** Set the value of an attribute.
	 * @param name Attribute name in SONAR namespace.
	 * @param v New attribute value.
	 * @return phantom object if one was created; null otherwise */
	public SonarObject setValue(Name name, String[] v)
		throws SonarException
	{
		String oname = name.getObjectPart();
		String aname = name.getAttributePart();
		synchronized(children) {
			SonarObject o = children.get(oname);
			if(o != null) {
				dispatcher.setValue(o, aname, v);
				return null;
			} else {
				o = dispatcher.createObject(oname);
				setField(o, aname, v);
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
	public SonarObject findObject(Checker c) {
		synchronized(children) {
			for(SonarObject o: children.values()) {
				if(c.check(o))
					return o;
			}
		}
		return null;
	}
}
