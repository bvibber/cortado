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

package com.fluendo.player;

import com.fluendo.utils.*;

import java.awt.*;
import java.io.*;
import java.util.*;
import com.jcraft.jogg.*;

public class PlayObject {
  private InputStream inputStream;
  private Component component;
  private Thread thread;

  public PlayObject() {
  }

  public void setInputStream(InputStream is) {
    inputStream = is;
  }
  public InputStream getInputStream() {
    return inputStream;
  }

  public Component getComponent() {
    return component;
  }

  public void stop() {
  }
  public void pause() {
  }
  public void play() {
    Cortado pt = new Cortado();
  }
}
