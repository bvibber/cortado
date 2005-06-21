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

package com.fluendo.examples;

import com.fluendo.player.*;
import java.awt.*;
import java.io.*;

public class Player {
  public static void main(String args[]) {
    Cortado c = new Cortado();

    Frame f = new Frame();

    f.add(c);
    f.setSize(f.getInsets().left+f.getInsets().right+384,f.getInsets().top+f.getInsets().bottom+268);
    f.show();

    //c.setParam("framerate", "5.0");
    c.setParam("framerate", "1.0");
    c.setParam("url", args[0]);
    c.setParam("local", "false");
    c.setParam("userId", "matthias");
    c.setParam("password", "roller-sk8");
    //c.setParam("bufferSize", "48");
    c.setParam("bufferSize", "200");
    c.setParam("preBuffer", "true");
    //c.setParam("preBuffer", "false");
    c.setParam("framerate", "-1");
    c.setParam("audio", "true");
    //c.setParam("audio", "false");
    c.setParam("video", "true");
    //c.setParam("debug", "5");
    c.init();
    c.start();
  }
}
