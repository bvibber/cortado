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

import java.awt.*;
import java.io.*;
import com.fluendo.plugin.*;
import com.fluendo.utils.*;

public class Demuxer implements Runnable 
{
  private DataConsumer audioConsumer;
  private DataConsumer videoConsumer;
  private InputStream inputStream;
  private Component component;
  private Plugin plugin;

  private boolean stopping;

  public Demuxer (InputStream is, Plugin pl, Component comp, DataConsumer ac, DataConsumer vc) {
    inputStream = is;
    audioConsumer = ac;
    videoConsumer = vc;
    component = comp;
    stopping = false;
    plugin = pl;
    pl.initDemuxer (is, comp, ac, vc);
  }

  public void run() {
    try {
      realRun();
    }
    catch (Throwable t) {
      Cortado.shutdown(t);
    }
  }
  private void realRun() {
    Debug.log(Debug.INFO, "entering demuxer thread");
    try {
      while (!stopping) {
        stopping = !plugin.demux();
      }
    }
    catch (Exception e) {
      if (!stopping)
        e.printStackTrace();
      stopping = true;
    }
    Debug.log(Debug.INFO, "exit demuxer thread");
  }

  public void stop() {
    plugin.stop();
    stopping = true;
  }
}
