/* Cortado - a video player java applet
 * Copyright (C) 2005 Fluendo S.L.
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

import com.fluendo.plugin.*;
import com.fluendo.jst.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class CortadoPipeline extends Pipeline implements PadListener {

  private String url;
  private String userId;
  private String password;
  private boolean enableAudio;
  private boolean enableVideo;
  private Component component;
  private int bufferSize = -1;
  private int bufferLow = -1;
  private int bufferHigh = -1;
	
  private Element httpsrc;
  private Element buffer;
  private Element demux;
  private Element videodec;
  private Element audiodec;
  private Element videosink;
  private Element audiosink;
  private Element v_queue, a_queue;

  public void padAdded(Pad pad) {
    Caps caps = pad.getCaps ();
    String mime = caps.getMime();
    
    if (enableAudio && mime.equals("audio/x-vorbis")) {
      pad.link(a_queue.getPad("sink"));
    }
    if (enableVideo && mime.equals("video/x-theora")) {
      pad.link(v_queue.getPad("sink"));
    }
  }
  
  public void padRemoved(Pad pad) {
    System.out.println ("pad removed "+pad);
  }

  public void noMorePads() {
    System.out.println ("no more pads");
  }

  public CortadoPipeline ()
  {
    super("pipeline");
    enableAudio = true;
    enableVideo = true;
  }

  public void setUrl(String anUrl) {
    url = anUrl;
  }
  public String getUrl() {
    return url;
  }
  public void setUserId(String aUserId) {
    userId = aUserId;
  }
  public void setPassword(String aPassword) {
    password = aPassword;
  }

  public void enableAudio(boolean b) {
    enableAudio = b;
  }
  public boolean isAudioEnabled() {
    return enableAudio;
  }

  public void enableVideo(boolean b) {
    enableVideo = b;
  }
  public boolean isVideoEnabled() {
    return enableVideo;
  }

  public void setComponent(Component c) {
    component = c;
  }
  public Component getComponent() {
    return component;
  }

  public void setBufferSize(int size) {
    bufferSize = size;
  }
  public int getBufferSize() {
    return bufferSize;
  }

  public void setBufferLow(int size) {
    bufferLow = size;
  }
  public int getBufferLow() {
    return bufferLow;
  }

  public void setBufferHigh(int size) {
    bufferHigh = size;
  }
  public int getBufferHigh() {
    return bufferHigh;
  }

  public boolean buildOgg()
  {
    demux = ElementFactory.makeByName("oggdemux");
    add(demux);

    buffer.getPad("src").link(demux.getPad("sink"));

    if (enableAudio) {
      a_queue = ElementFactory.makeByName("queue");
      audiodec = ElementFactory.makeByName("vorbisdec");
      audiosink = ElementFactory.makeByName("audiosinkj2");

      add(a_queue);
      add(audiodec);
      add(audiosink);

      a_queue.getPad("src").link(audiodec.getPad("sink"));
      audiodec.getPad("src").link(audiosink.getPad("sink"));
    }
    if (enableVideo) {
      v_queue = ElementFactory.makeByName("queue");
      videodec = ElementFactory.makeByName("theoradec");
      videosink = ElementFactory.makeByName("videosink");
      videosink.setProperty ("component", component);

      add(v_queue);
      add(videodec);
      add(videosink);
      
      v_queue.getPad("src").link(videodec.getPad("sink"));
      videodec.getPad("src").link(videosink.getPad("sink"));
    }

    demux.addPadListener (this);

    return true;
  }

  public boolean buildMultipart()
  {
    demux = ElementFactory.makeByName("multipartdemux");
    add(demux);

    buffer.getPad("src").link(demux.getPad("sink"));

    if (enableAudio) {
      a_queue = ElementFactory.makeByName("queue");
      audiodec = ElementFactory.makeByName("vorbisdec");
      audiosink = ElementFactory.makeByName("audiosinkj2");

      add(a_queue);
      add(audiodec);
      add(audiosink);

      a_queue.getPad("src").link(audiodec.getPad("sink"));
      audiodec.getPad("src").link(audiosink.getPad("sink"));
    }
    if (enableVideo) {
      v_queue = ElementFactory.makeByName("queue");
      videodec = ElementFactory.makeByName("theoradec");
      videosink = ElementFactory.makeByName("videosink");
      videosink.setProperty ("component", component);

      add(v_queue);
      add(videodec);
      add(videosink);
      
      v_queue.getPad("src").link(videodec.getPad("sink"));
      videodec.getPad("src").link(videosink.getPad("sink"));
    }

    demux.addPadListener (this);

    return true;
  }

  public boolean build()
  {
    boolean res;

    httpsrc = ElementFactory.makeByName("httpsrc");
    httpsrc.setProperty("url", url);
    httpsrc.setProperty("userId", userId);
    httpsrc.setProperty("password", password);
    add(httpsrc);

    buffer = ElementFactory.makeByName("queue");
    buffer.setProperty("isBuffer", Boolean.TRUE);
    if (bufferSize != -1)
      buffer.setProperty("maxSize", new Integer (bufferSize * 1024));
    if (bufferLow != -1)
      buffer.setProperty("lowPercent", new Integer (bufferLow));
    if (bufferHigh != -1)
      buffer.setProperty("highercent", new Integer (bufferHigh));
    add(buffer);

    httpsrc.getPad("src").link(buffer.getPad("sink"));

    //res = buildOgg();
    res = buildMultipart();

    return res;
  }

  protected boolean doSendEvent(com.fluendo.jst.Event event) {
    boolean res;

    if (event.getType() != com.fluendo.jst.Event.SEEK)
      return false;

    if (event.parseSeekFormat() != Format.PERCENT)
      return false;

    res = httpsrc.getPad("src").sendEvent (event);
    getState(null, null, 50 * Clock.MSECOND);

    return res;
  }

  protected long getPosition() {
    Query q;
    long result = 0;

    q = Query.newPosition(Format.TIME);
    if (super.query (q)) {
      result = q.parsePositionValue (); 
    }
    return result;
  }
}
