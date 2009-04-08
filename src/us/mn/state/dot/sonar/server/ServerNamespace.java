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
import us.mn.state.dot.sonar.FlushError;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.MessageEncoder;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * A SONAR namespace is a mapping from all SONAR names to types, objects and
 * attributes.
 *
 * @author Douglas Lau
 */
public class ServerNamespace extends Namespace {

	/** All SONAR types are stored in the root of the namespace */
	protected final HashMap<String, TypeNode> root =
		new HashMap<String, TypeNode>();

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
	protected TypeNode getTypeNode(Name name) throws NamespaceError {
		TypeNode t = _getTypeNode(name.getTypePart());
		if(t == null)
			throw NamespaceError.nameUnknown(name.toString());
		else
			return t;
	}

	/** Set the value of an attribute.
	 * @param name Attribute name in SONAR namespace.
	 * @param v New attribute value.
	 * @return phantom object if one was created; null otherwise */
	SonarObject setAttribute(Name name, String[] v) throws SonarException {
		TypeNode t = getTypeNode(name);
		return t.setValue(name, v);
	}

	/** Set the value of an attribute on a phantom object.
	 * @param name Attribute name in SONAR namespace.
	 * @param v New attribute value.
	 * @param phantom Phantom object to set attribute on. */
	void setAttribute(Name name, String[] v, SonarObject phantom)
		throws SonarException
	{
		TypeNode t = getTypeNode(name);
		t.setField(phantom, name.getAttributePart(), v);
	}

	/** Get the value of an attribute */
	String[] getAttribute(Name name) throws SonarException {
		TypeNode t = getTypeNode(name);
		SonarObject o = t.lookupObject(name.getObjectPart());
		if(o != null)
			return t.getValue(o, name.getAttributePart());
		else
			throw NamespaceError.NAME_INVALID;
	}

	/** Remove an object from the namespace */
	void removeObject(SonarObject o) throws SonarException {
		TypeNode n = getTypeNode(o);
		n.removeObject(o);
	}

	/** Lookup the object with the specified name */
	SonarObject lookupObject(Name name) {
		if(name.isObject()) {
			TypeNode t = _getTypeNode(name.getTypePart());
			if(t != null)
				return t.lookupObject(name.getObjectPart());
		}
		return null;
	}

	/** Enumerate the root of the namespace */
	protected void enumerateRoot(MessageEncoder enc)
		throws FlushError
	{
		synchronized(root) {
			for(TypeNode t: root.values())
				enc.encode(Message.TYPE, t.name);
		}
		enc.encode(Message.TYPE);
	}

	/** Enumerate all objects of the named type */
	protected void enumerateType(MessageEncoder enc, Name name)
		throws NamespaceError, SonarException
	{
		TypeNode t = getTypeNode(name);
		enc.encode(Message.TYPE, name.getTypePart());
		t.enumerateObjects(enc);
		enc.encode(Message.TYPE);
	}

	/** Enumerate all attributes of the named object */
	protected void enumerateObject(MessageEncoder enc, SonarObject o)
		throws SonarException
	{
		TypeNode t = getTypeNode(o);
		t.enumerateObject(enc, o);
	}

	/** Enumerate all attributes of the named object */
	protected void enumerateObject(MessageEncoder enc, Name name)
		throws SonarException
	{
		SonarObject o = lookupObject(name);
		if(o != null)
			enumerateObject(enc, o);
		else
			throw NamespaceError.NAME_INVALID;
	}

	/** Enumerate a named attribute */
	protected void enumerateAttribute(MessageEncoder enc, Name name)
		throws SonarException
	{
		if(name.getObjectPart().equals("")) {
			TypeNode t = getTypeNode(name);
			t.enumerateAttribute(enc, name.getAttributePart());
		} else {
			String[] v = getAttribute(name);
			enc.encode(Message.ATTRIBUTE, name.toString(), v);
		}
	}

	/** Enumerate everything contained by a name in the namespace */
	void enumerate(Name name, MessageEncoder enc) throws SonarException {
		if(name.isRoot())
			enumerateRoot(enc);
		else if(name.isType())
			enumerateType(enc, name);
		else if(name.isObject())
			enumerateObject(enc, name);
		else if(name.isAttribute())
			enumerateAttribute(enc, name);
		else
			throw NamespaceError.NAME_INVALID;
	}

	/** Register a new type in the namespace */
	public TypeNode registerType(String n, Class c) {
		TypeNode node = new TypeNode(this, n, c);
		synchronized(root) {
			root.put(n, node);
		}
		return node;
	}

	/** Add an object into the namespace without storing */
	public void addObject(SonarObject o) throws NamespaceError {
		getTypeNode(o).addObject(o);
	}

	/** Store an object in the namespace */
	public void storeObject(SonarObject o) throws SonarException {
		getTypeNode(o).storeObject(o);
	}

	/** Create a new object */
	public SonarObject createObject(Name name) throws SonarException {
		TypeNode n = getTypeNode(name);
		return n.createObject(name.getObjectPart());
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name
	 * @param oname Sonar object name
	 * @return Object from namespace or null if name does not exist */
	public SonarObject lookupObject(String tname, String oname) {
		return lookupObject(new Name(tname, oname));
	}

	/** Find an object by calling a checker for each object of a type.
	 * @param tname Sonar type name
	 * @param c Checker callback
	 * @return First object which Checker returns true */
	public SonarObject findObject(String tname, Checker c) {
		TypeNode t = _getTypeNode(tname);
		if(t != null)
			return t.findObject(c);
		else
			return null;
	}
}
