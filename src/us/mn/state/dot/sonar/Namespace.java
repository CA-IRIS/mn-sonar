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
package us.mn.state.dot.sonar;

/**
 * This helper class provides static methods to manipulate SONAR names.
 *
 * @author Douglas Lau
 */
abstract public class Namespace {

	/** Name separator */
	static public final String SEP = "/";

	/** Empty namespace path */
	static protected final String[] NULL_PATH = {};

	/** Parse a SONAR path */
	static public String[] parse(String path) {
		if(path.length() == 0)
			return NULL_PATH;
		else
			return path.split(SEP);
	}

	/** Make a SONAR path from an object */
	static public String makePath(SonarObject o) {
		return o.getTypeName() + SEP + o.getName();
	}

	/** Make a SONAR path from an object/attribute pair */
	static public String makePath(SonarObject o, String a) {
		return o.getTypeName() + SEP + o.getName() + SEP + a;
	}

	/** Make a SONAR path from a type/object pair */
	static public String makePath(String tname, String oname) {
		return tname + SEP + oname;
	}

	/** Make a SONAR path from a type/object/attribute tuple */
	static public String makePath(String tnm, String onm, String anm) {
		return tnm + SEP + onm + SEP + anm;
	}

	/** Test if a SONAR path is absolute (versus relative) */
	static public boolean isAbsolute(String path) {
		return path.contains(SEP);
	}

	/** FIXME: Hack to allow static name lookups */
	static protected Namespace _namespace;

	/** FIXME: Lookup the specified object */
	static public SonarObject lookup(String tname, String oname)
		throws NamespaceError
	{
		return _namespace.lookupObject(tname, oname);
	}

	/** Lookup an object in the namespace */
	abstract protected SonarObject lookupObject(String tname, String oname)
		throws NamespaceError;
}
