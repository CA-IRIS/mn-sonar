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
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.MessageEncoder;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * A SONAR namespace is a mapping from all SONAR names to types, objects and
 * attributes.
 *
 * @author Douglas Lau
 */
public class Namespace extends Names {

	/** All SONAR types are stored in the root of the namespace */
	protected final HashMap<String, TypeNode> root =
		new HashMap<String, TypeNode>();

	/** Create a new SONAR namespace */
	public Namespace() {
		_namespace = this;
	}

	/** Register a new type in the namespace */
	public TypeNode registerType(String n, Class c) {
		TypeNode node = new TypeNode(n, c);
		synchronized(root) {
			root.put(n, node);
		}
		return node;
	}

	/** Register a new type in the namespace */
	protected TypeNode registerType(SonarObject o) {
		return registerType(o.getTypeName(), o.getClass());
	}

	/** Get a type node from the namespace */
	protected TypeNode _getTypeNode(String t) {
		synchronized(root) {
			return root.get(t);
		}
	}

	/** Get a type node from the namespace */
	protected TypeNode getTypeNode(SonarObject o) {
		TypeNode n = _getTypeNode(o.getTypeName());
		if(n == null)
			return registerType(o);
		else
			return n;
	}

	/** Get a type node from the namespace by name */
	protected TypeNode getTypeNode(String name) throws NamespaceError {
		TypeNode t = _getTypeNode(name);
		if(t == null)
			throw NamespaceError.NAME_UNKNOWN;
		else
			return t;
	}

	/** Add an object into the namespace */
	public void add(SonarObject o) throws NamespaceError {
		TypeNode n = getTypeNode(o);
		n.add(o);
	}

	/** Create a new object */
	public SonarObject createObject(String tname, String oname)
		throws SonarException
	{
		TypeNode n = getTypeNode(tname);
		return n.createObject(oname);
	}

	/** Store an object in the namespace */
	public void storeObject(SonarObject o) throws SonarException {
		getTypeNode(o).storeObject(o);
	}

	/** Set the value of an attribute */
	public SonarObject setAttribute(String tname, String oname,
		String aname, String[] params, SonarObject phantom)
		throws SonarException
	{
		TypeNode t = getTypeNode(tname);
		if(phantom != null && phantom.getTypeName().equals(tname) &&
			phantom.getName().equals(oname))
		{
			t.setField(phantom, aname, params);
			return phantom;
		}
		return t.setValue(oname, aname, params);
	}

	/** Remove an object from the namespace */
	public SonarObject removeObject(SonarObject o) throws SonarException {
		TypeNode n = getTypeNode(o);
		return n.removeObject(o);
	}

	/** Lookup the specified object */
	public SonarObject lookupObject(String tname, String oname)
		throws NamespaceError
	{
		TypeNode t = getTypeNode(tname);
		return t.lookupObject(oname);
	}

	/** Lookup the specified object by name */
	public SonarObject lookupObject(String name) throws NamespaceError {
		String[] names = parse(name);
		if(names.length != 2)
			throw NamespaceError.NAME_INVALID;
		else
			return lookupObject(names[0], names[1]);
	}

	/** Remove an object from the namespace */
	public SonarObject removeObject(String name) throws SonarException {
		SonarObject o = lookupObject(name);
		return removeObject(o);
	}

	/** Enumerate the root of the namespace */
	protected void enumerateRoot(MessageEncoder enc) {
		synchronized(root) {
			for(TypeNode t: root.values())
				enc.encode(Message.TYPE, t.name);
		}
		enc.encode(Message.TYPE);
	}

	/** Enumerate all objects of the named type */
	protected void enumerateType(MessageEncoder enc, String name)
		throws NamespaceError, SonarException
	{
		TypeNode t = getTypeNode(name);
		enc.encode(Message.TYPE, name);
		t.enumerateObjects(enc);
		enc.encode(Message.TYPE);
	}

	/** Enumerate all attributes of the named object */
	protected void enumerateObject(MessageEncoder enc, SonarObject o)
		throws SonarException
	{
		TypeNode t = getTypeNode(o.getTypeName());
		t.enumerateObject(enc, o);
	}

	/** Enumerate all attributes of the named object */
	protected void enumerateObject(MessageEncoder enc, String[] names)
		throws SonarException
	{
		SonarObject o = lookupObject(names[0], names[1]);
		enumerateObject(enc, o);
	}

	/** Enumerate a named attribute */
	protected void enumerateAttribute(MessageEncoder enc, String name,
		String[] names) throws SonarException
	{
		TypeNode t = getTypeNode(names[0]);
		SonarObject o = t.lookupObject(names[1]);
		String[] v = t.getValue(o, names[2]);
		enc.encode(Message.ATTRIBUTE, name, v);
	}

	/** Enumerate everything contained by a name in the namespace */
	public void enumerate(String name, MessageEncoder enc)
		throws SonarException
	{
		String[] names = parse(name);
		switch(names.length) {
			case 0:
				enumerateRoot(enc);
				return;
			case 1:
				enumerateType(enc, name);
				return;
			case 2:
				enumerateObject(enc, names);
				return;
			case 3:
				enumerateAttribute(enc, name, names);
				return;
		}
		throw NamespaceError.NAME_INVALID;
	}

	/** Find an object with the specified type name and checker callback */
	public SonarObject findObject(String tname, Checker c)
		throws NamespaceError
	{
		TypeNode t = getTypeNode(tname);
		return t.find(c);
	}
}
