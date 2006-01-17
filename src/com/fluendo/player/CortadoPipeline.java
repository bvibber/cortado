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

import com.fluendo.jst.*;
import java.awt.*;

public class CortadoPipeline extends Pipeline implements PadListener, CapsListener {

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

    if (caps == null) {
      System.out.println("pad added without caps");
      return;
    }

    String mime = caps.getMime();
    
    if (enableAudio && mime.equals("audio/x-vorbis")) {
      a_queue = ElementFactory.makeByName("queue", "a_queue");
      audiodec = ElementFactory.makeByName("vorbisdec", "audiodec");

      add(a_queue);
      add(audiodec);

      pad.link(a_queue.getPad("sink"));
      a_queue.getPad("src").link(audiodec.getPad("sink"));
      audiodec.getPad("src").link(audiosink.getPad("sink"));

      audiodec.setState (PAUSE);
      a_queue.setState (PAUSE);
    }
    else if (enableVideo && mime.equals("video/x-theora")) {
      v_queue = ElementFactory.makeByName("queue", "v_queue");
      videodec = ElementFactory.makeByName("theoradec", "videodec");

      add(v_queue);
      add(videodec);

      pad.link(v_queue.getPad("sink"));
      v_queue.getPad("src").link(videodec.getPad("sink"));
      videodec.getPad("src").link(videosink.getPad("sink"));

      videodec.setState (PAUSE);
      v_queue.setState (PAUSE);
    }
    else if (enableVideo && mime.equals("image/jpeg")) {
      videodec = ElementFactory.makeByName("jpegdec", "videodec");
      videodec.setProperty ("component", component);

      add(videodec);
      
      videodec.getPad("src").link(videosink.getPad("sink"));
      pad.link(videodec.getPad("sink"));

      videodec.setState (PAUSE);
    }
    else if (enableVideo && mime.equals("video/x-smoke")) {
      videodec = ElementFactory.makeByName("smokedec", "videodec");
      videodec.setProperty ("component", component);

      add(videodec);
      
      videodec.getPad("src").link(videosink.getPad("sink"));
      pad.link(videodec.getPad("sink"));

      videodec.setState (PAUSE);
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
    demux = ElementFactory.makeByName("oggdemux", "demux");
    add(demux);

    buffer = ElementFactory.makeByName("queue", "buffer");
    buffer.setProperty("isBuffer", Boolean.TRUE);
    if (bufferSize != -1)
      buffer.setProperty("maxSize", new Integer (bufferSize * 1024));
    if (bufferLow != -1)
      buffer.setProperty("lowPercent", new Integer (bufferLow));
    if (bufferHigh != -1)
      buffer.setProperty("highercent", new Integer (bufferHigh));
    add(buffer);

    httpsrc.getPad("src").link(buffer.getPad("sink"));
    buffer.getPad("src").link(demux.getPad("sink"));
    demux.addPadListener (this);

    buffer.setState(PAUSE);
    demux.setState(PAUSE);

    return true;
  }

  public boolean buildMultipart()
  {
    demux = ElementFactory.makeByName("multipartdemux", "demux");
    add(demux);

    httpsrc.getPad("src").link(demux.getPad("sink"));

    demux.addPadListener (this);

    return true;
  }

  public void capsChanged(Caps caps) {
    if (caps.getMime().equals ("application/ogg")) {
      buildOgg();
    }
    else if (caps.getMime().equals ("multipart/x-mixed-replace")) {
      buildMultipart();
    }
  }

  public boolean build()
  {
    boolean res;

    httpsrc = ElementFactory.makeByName("httpsrc", "httpsrc");
    httpsrc.setProperty("url", url);
    httpsrc.setProperty("userId", userId);
    httpsrc.setProperty("password", password);
    add(httpsrc);

    httpsrc.getPad("src").addCapsListener (this);

    if (enableAudio) {
      //audiosink = ElementFactory.makeByName("audiosinkj2", "audiosink");
      audiosink = ElementFactory.makeByName("audiosinksa", "audiosink");

      add(audiosink);
    }
    if (enableVideo) {
      videosink = ElementFactory.makeByName("videosink", "videosink");
      videosink.setProperty ("component", component);

      add(videosink);
    }

    //res = buildOgg();
    //res = buildMultipartJPEG();
    //res = buildMultipartSmoke();

    return true;
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
