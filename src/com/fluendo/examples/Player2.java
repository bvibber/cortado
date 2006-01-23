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

import com.fluendo.jst.*;
import java.awt.*;
import java.awt.event.*;

class PlayPipeline extends Pipeline implements PadListener {
  private Element httpsrc;
  private Element oggdemux;
  private Element theoradec;
  private Element vorbisdec;
  private Element videosink;
  private Element audiosink;
  private Element v_queue, a_queue;

  public void padAdded(Pad pad) {
    Caps caps = pad.getCaps ();
    String mime = caps.getMime();
    
    if (mime.equals("audio/x-vorbis")) {
      pad.link(a_queue.getPad("sink"));
    }
    if (mime.equals("video/x-theora")) {
      pad.link(v_queue.getPad("sink"));
    }
  }
  
  public void padRemoved(Pad pad) {
    System.out.println ("pad removed "+pad);
  }

  public void noMorePads() {
    System.out.println ("no more pads");
  }

  public PlayPipeline (String uri)
  {
    httpsrc = ElementFactory.makeByName("httpsrc", "httpsrc");
    httpsrc.setProperty("url", uri);

    oggdemux = ElementFactory.makeByName("oggdemux", "oggdemux");
    theoradec = ElementFactory.makeByName("theoradec", "theoradec");
    vorbisdec = ElementFactory.makeByName("vorbisdec", "vorbisdec");
    audiosink = ElementFactory.makeByName("audiosinkj2", "audiosink");
    videosink = ElementFactory.makeByName("videosink", "videosink");
    v_queue = ElementFactory.makeByName("queue", "v_queue");
    a_queue = ElementFactory.makeByName("queue", "a_queue");

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
  }

  protected boolean doSendEvent(com.fluendo.jst.Event event) {
    if (event.getType() != com.fluendo.jst.Event.SEEK)
      return false;

    httpsrc.getPad("src").sendEvent (event);
    getState(null, null, 0);
    streamTime = 0;

    return true;
  }
}


class PlayFrame extends Frame implements AdjustmentListener {
private static final long serialVersionUID = 1L;
private Scrollbar sb;
  private PlayPipeline pipeline;
  
  public PlayFrame(PlayPipeline p) {
    pipeline = p;
    sb = new Scrollbar(Scrollbar.HORIZONTAL, 0, 2, 0, 100);  
    sb.setSize(200, 16);
    sb.addAdjustmentListener (this);
    setSize(200,32);
    add(sb);
    pack (); 
  }
  
  public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
    if (!e.getValueIsAdjusting()) {
      int val;
      com.fluendo.jst.Event event;

      /* get value, convert to PERCENT and construct seek event */
      val = e.getValue();
      event = com.fluendo.jst.Event.newSeek (Format.PERCENT, val * Format.PERCENT_SCALE);

      /* send event to pipeline */
      pipeline.sendEvent(event);
    }
  }
}

public class Player2 implements BusHandler {
  private PlayPipeline pipeline;
  private Bus bus;
  private Frame frame;

  public Player2 (String uri)
  {
    pipeline = new PlayPipeline(uri);
    bus = pipeline.getBus();
    bus.addHandler (this);

    frame = new PlayFrame(pipeline);
    frame.show();

    pipeline.setState (Element.PLAY);
  }

  public void handleMessage (Message msg)
  {
    switch (msg.getType()) {
      case Message.WARNING:
      case Message.ERROR:
        System.out.println ("got ERROR from "+msg.getSrc()+ ": "+msg);
	break;
      case Message.EOS:
        System.out.println ("got EOS from "+msg.getSrc()+ ": "+msg);
	break;
      default:	
	break;
    }
  }

  public static void main(String args[]) {
    if (args.length < 1) {
      System.out.println ("usage: Player2 <uri>");
      return;
    }
    
    Player2 p2 = new Player2(args[0]);

    synchronized (p2) {
      try {
        p2.wait ();
      }
      catch (InterruptedException ie) {}
    }
  }
}
