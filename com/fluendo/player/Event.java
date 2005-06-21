/* Cortado - a video player java applet
 * Copyright (C) 2004 Fluendo S.L.
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Street #330, Boston, MA 02111-1307, USA.
 */

package com.fluendo.jst;

import java.util.*;

public class Event {

  /* types */
  public static final int EOS = 1;
  public static final int FLUSH_START = 2;
  public static final int FLUSH_END = 3;
  public static final int SEEK = 4;
  public static final int DISCONT = 5;
	
  private int type;
  private int format;
  private long position;

  private Event(int type) {
    position = -1;
    this.type = type;
  }

  public int getType () {
    return type;
  }

  public static Event newEOS() {
    return new Event(EOS);
  }

  public static Event newFlushStart() {
    return new Event(FLUSH_START);
  }
  public static Event newFlushEnd() {
    return new Event(FLUSH_END);
  }

  public static Event newSeek(int format, long position) {
    Event e = new Event(SEEK);
    e.format = format;
    e.position = position;
    return e;
  }
  public long getSeekPosition () {
    return position;
  }
  public int getSeekFormat () {
    return format;
  }

  public static Event newDiscont(int format, long position) {
    Event e = new Event(DISCONT);
    e.format = format;
    e.position = position;
    return e;
  }
  public long getDiscontPosition () {
    return position;
  }
  public int getDiscontFormat () {
    return format;
  }
}
