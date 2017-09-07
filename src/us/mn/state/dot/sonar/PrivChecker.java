/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2017  Minnesota Department of Transportation
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
 * Interface for checking privilege.
 *
 * @author Douglas Lau
 */
public interface PrivChecker {

	/** Check for read privilege.
	 * @param p Privilege to check. */
	boolean checkRead(Privilege p);

	/** Check for write privilege.
	 * @param p Privilege to check. */
	boolean checkWrite(Privilege p);
}
