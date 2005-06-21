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

import com.fluendo.plugin.*;
import com.fluendo.jst.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class PlayPipeline extends Pipeline implements PadListener {
  private Element httpsrc;
  private Element oggdemux;
  private Element theoradec;
  private Element vorbisdec;
  private Element videosink;
  private Element audiosink;
  private Element v_queue, a_queue;

  public void newPad(Pad pad) {
    if (pad.getName().equals("serial_31273")) {
      pad.link(a_queue.getPad("sink"));
    }
    if (pad.getName().equals("serial_31272")) {
      pad.link(v_queue.getPad("sink"));
    }
  }
  
  public void padRemoved(Pad pad) {
    System.out.println ("pad removed "+pad);
  }

  public void noMorePads() {
    System.out.println ("no more pads");
  }

  public PlayPipeline ()
  {
    httpsrc = ElementFactory.makeByName("httpsrc");
    //httpsrc.setProperty("url", "http://localhost/fluendo/src/fluendo/psvn/cortado/trunk/test8.ogg");
    httpsrc.setProperty("url", "http://localhost/fluendo/src/fluendo/psvn/cortado/trunk/sanju.ogg");

    oggdemux = ElementFactory.makeByName("oggdemux");
    theoradec = ElementFactory.makeByName("theoradec");
    vorbisdec = ElementFactory.makeByName("vorbisdec");
    audiosink = ElementFactory.makeByName("audiosinkj2");
    videosink = ElementFactory.makeByName("videosink");
    v_queue = new Queue();
    a_queue = new Queue();

    oggdemux.addPadListener (this);

    add(httpsrc);
    add(oggdemux);
    add(theoradec);
    add(vorbisdec);
    add(videosink);
    add(audiosink);
    add(v_queue);
    add(a_queue);

    httpsrc.getPad("src").link(oggdemux.getPad("sink"));

    v_queue.getPad("src").link(theoradec.getPad("sink"));
    theoradec.getPad("src").link(videosink.getPad("sink"));

    a_queue.getPad("src").link(vorbisdec.getPad("sink"));
    vorbisdec.getPad("src").link(audiosink.getPad("sink"));
    
    setState (Element.PAUSE);
    getState(null, null, 0);
    setState (Element.PLAY);
  }

  protected int doChildStateChange() {
    super.doChildStateChange();

    videosink.setState(pending);
    audiosink.setState(pending);
    vorbisdec.setState(pending);
    theoradec.setState(pending);
    v_queue.setState(pending);
    a_queue.setState(pending);
    oggdemux.setState(pending);
    httpsrc.setState(pending);

    return SUCCESS;
  }

  public boolean seek(long offset) {

    setState (Element.PAUSE);
    httpsrc.getPad("src").sendEvent (com.fluendo.jst.Event.newSeek(Format.BYTES, offset));
    getState(null, null, 0);

    long t1 = ((Sink)videosink).getPrerollTime();
    long t2 = ((Sink)audiosink).getPrerollTime();

    streamTime = Math.min (t1, t2);

    setState (Element.PLAY);

    return true;
  }
}


class PlayFrame extends Frame implements AdjustmentListener {
  private Scrollbar sb;
  private PlayPipeline pipeline;
  
  public PlayFrame(PlayPipeline p) {
    pipeline = p;
    sb = new Scrollbar(Scrollbar.HORIZONTAL, 0, 2, 0, 4124322);  
    sb.setSize(200, 16);
    sb.addAdjustmentListener (this);
    setSize(200,32);
    add(sb);
    
  }
  
  public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
    if (!e.getValueIsAdjusting()) {
      int val = e.getValue();
      pipeline.seek(val); 
    }
  }
}

public class Player2 {
  private PlayPipeline pipeline;
  private Frame frame;

  public Player2 ()
  {
    pipeline = new PlayPipeline();
    frame = new PlayFrame(pipeline);
    frame.show();
  }

  public static void main(String args[]) {
    Player2 p2 = new Player2();

    synchronized (p2) {
      try {
        p2.wait ();
      }
      catch (InterruptedException ie) {}
    }
  }
}
