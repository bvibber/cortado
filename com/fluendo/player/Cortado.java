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

import java.applet.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.util.*;
import com.fluendo.utils.*;
import com.fluendo.jst.*;

public class Cortado extends Applet implements 
                Runnable, 
		MouseMotionListener,
		MouseListener,
		BusHandler,
		StatusListener
{
  private static Cortado cortado;
  private static CortadoPipeline pipeline;

  private String urlString;
  private boolean local;
  private boolean seekable;
  private double framerate;
  private boolean audio;
  private boolean video;
  private boolean keepAspect;
  private int bufferSize;
  private String userId;
  private String password;
  private boolean usePrebuffer;
  private int bufferLow;
  private int bufferHigh;
  private int debug;
  private long duration;

  private Thread statusThread;
  private Status status;
  private boolean inStatus;
  private PopupMenu menu;
  private boolean stopping;
  private Hashtable params = new Hashtable();
  private Configure configure;

  private Dimension appletDimension;

  public String getAppletInfo() {
    return "Title: Fluendo media player \nAuthor: Wim Taymans \nA Java based network multimedia player.";
  }

  public String[][] getParameterInfo() {
    String[][] info = {
      {"url",         "URL",     "The media file to play"},
      {"local",       "boolean", "Is this a local file (default false)"},
      {"seekable",    "boolean", "Can you seek in this file (default false)"},
      {"duration",    "string",  "Total duration of the file in hmmss (default unknown)"},
      {"framerate",   "float",   "The default framerate of the video (default 5.0)"},
      {"audio",       "boolean", "Enable audio playback (default true)"},
      {"video",       "boolean", "Enable video playback (default true)"},
      {"keepAspect",  "boolean", "Use aspect ratio of video (default true)"},
      {"preBuffer",   "boolean", "Use Prebuffering (default = true)"},
      {"doubleBuffer","boolean", "Use double buffering for screen updates (default = true)"},
      {"bufferSize",  "int",     "The size of the prebuffer in Kbytes (default 100)"},
      {"bufferLow",   "int",     "Percent of empty buffer (default 10)"},
      {"bufferHigh",  "int",     "Percent of full buffer (default 70)"},
      {"userId",      "string",  "userId for basic authentication (default null)"},
      {"password",    "string",  "password for basic authentication (default null)"},
      {"debug",       "int",     "Debug level 0 - 4 (default = 3)"},
    };
    return info;
  }

  public void setParam(String name, String value) {
    params.put(name, value);
  }

  public void restart() {
    stop();
    init();
    start();
  }

  public String getParam(String name, String def)
  {
    String result;

    result = (String) params.get(name);

    if (result == null) {
      try {
        result = getParameter(name);
      }
      catch (Exception e) { 
      }
    }
    if (result == null) {
      result = def;
    }
    return result;
  }
  
  public static void shutdown(Throwable error) {
    Debug.log(Debug.INFO, "shutting down: reason: "+error.getMessage());
    error.printStackTrace();
    cortado.stop();
  }

  private long stringToTime (String d) {
    int hours, min, sec;
    int len;

    if (d == null)
      return -1;
    
    len = d.length();

    sec = Integer.valueOf(d.substring(len-2)).intValue();
    min = Integer.valueOf(d.substring(len-4, len-2)).intValue();
    hours = Integer.valueOf(d.substring(0, len-4)).intValue();
    
    System.out.println(""+hours+":"+min+":"+sec);

    return ((hours * 60) + min) * 60 + sec;
  }

  public void init() {
    cortado = this;

    pipeline = new CortadoPipeline();
    configure = new Configure();

    urlString = getParam("url", null);
    local = String.valueOf(getParam("local", "false")).equals("true");
    seekable = String.valueOf(getParam("seekable", "false")).equals("true");
    duration = stringToTime (getParam("duration",  null));
    framerate = Double.valueOf(getParam("framerate", "5.0")).doubleValue();
    audio = String.valueOf(getParam("audio","true")).equals("true");
    video = String.valueOf(getParam("video","true")).equals("true");
    keepAspect = String.valueOf(getParam("keepAspect","true")).equals("true");
    usePrebuffer = String.valueOf(getParam("preBuffer","true")).equals("true");
    bufferSize = Integer.valueOf(getParam("bufferSize","200")).intValue();
    bufferLow = Integer.valueOf(getParam("bufferLow","10")).intValue();
    bufferHigh = Integer.valueOf(getParam("bufferHigh","70")).intValue();
    debug = Integer.valueOf(getParam("debug","3")).intValue();
    userId = getParam("userId",  null);
    password = getParam("password",  null);

    Debug.level = debug;
    Debug.log(Debug.WARNING, "build info: " + configure.buildInfo);

    pipeline.setUrl (urlString);
    pipeline.setUserId (userId);
    pipeline.setPassword (password);
    pipeline.enableAudio (audio);
    pipeline.enableVideo (video);
    pipeline.setBufferSize (bufferSize);
    pipeline.setBufferLow (bufferLow);
    pipeline.setBufferHigh (bufferHigh);

    pipeline.setComponent (this);
    pipeline.getBus().addHandler (this);
    pipeline.build();

    setBackground(Color.black);
    setForeground(Color.white);

    status = new Status(this);
    status.setVisible(true);
    status.setHaveAudio (audio);
    status.setHavePercent (true);
    status.setSeekable (seekable);
    status.setDuration (duration);
    inStatus = false;

    menu = new PopupMenu();
    menu.add("About...");
    this.add (menu);
  }

  public synchronized Graphics getGraphics() {
    Graphics g = super.getGraphics();

    if (status.isVisible()) {
      g.setClip (0, 0, getSize().width, getSize().height-12); 
    }
    else {
      g.setClip (0, 0, getSize().width, getSize().height); 
    }
    return g;
  }
  public synchronized Dimension getSize() {
    if (appletDimension == null)
      appletDimension = super.getSize();

    return appletDimension;
  }

  public synchronized void update(Graphics g) {
    paint(g);
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
    Debug.log(Debug.INFO, "entering status thread");
    while (!stopping) {
      try {
	status.setTime (pipeline.getPosition() / Clock.SECOND);

        Thread.sleep(1000);
      }
      catch (Exception e) {
        if (!stopping)
          e.printStackTrace();
      }
    }
    Debug.log(Debug.INFO, "exit status thread");
  }

  public synchronized void paint(Graphics g) 
  {
    int dwidth = getSize().width;
    int dheight = getSize().height;

    if (status != null && status.isVisible()) {
      status.setBounds(0, dheight-12, dwidth, 12);
      status.paint(g);
    }
  }

  private void setStatusVisible (boolean b) {
    /* no change, do nothing */
    if (status.isVisible() == b)
      return;

    /* don't make invisible when the mouse pointer is inside
     * status area */
    if (inStatus && !b)
      return;

    status.setVisible(b);
    repaint();
  }

  private boolean intersectStatus (MouseEvent e) {
    inStatus = e.getY() > getSize().height-12; 
    return inStatus;
  }

  public void mouseClicked(MouseEvent e){}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) 
  {
    setStatusVisible(false);
  }
  public void mousePressed(MouseEvent e) 
  {
    if (intersectStatus(e)) {
      int y = getSize().height-12;
      e.translatePoint (0, -y);
      ((MouseListener)status).mousePressed (e);
    }
    else {
      if ((e.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK) {
        menu.show(this, e.getX(), e.getY());
      }
    }
  }
  public void mouseReleased(MouseEvent e) 
  {
    if (intersectStatus(e)) {
      int y = getSize().height-12;
      e.translatePoint (0, -y);
      ((MouseListener)status).mouseReleased (e);
    }
  }

  public void mouseDragged(MouseEvent e)
  {
    if (intersectStatus(e)) {
      int y = getSize().height-12;
      setStatusVisible(true);
      e.translatePoint (0, -y);
      ((MouseMotionListener)status).mouseDragged (e);
    }
    else {
      setStatusVisible(false);
    }
  }
  public void mouseMoved(MouseEvent e)
  {
    if (intersectStatus(e)) {
      int y = getSize().height-12;
      setStatusVisible(true);
      e.translatePoint (0, -y);
      ((MouseMotionListener)status).mouseMoved (e);
    }
    else {
      setStatusVisible(false);
    }
  }

  public void handleMessage (Message msg)
  {
    switch (msg.getType()) {
      case Message.WARNING:
      case Message.ERROR:
        System.out.println(msg.toString());
        status.setMessage (msg.parseErrorString());
	status.setState(Status.STATE_STOPPED);
        setStatusVisible(true);
        break;
      case Message.EOS:
        System.out.println(msg.toString());
        status.setMessage ("Playback ended");
        break;
      case Message.STREAM_STATUS:
        System.out.println(msg.toString());
        break;
      case Message.RESOURCE:
        status.setMessage (msg.parseResourceString());
        setStatusVisible(true);
        break;
      case Message.BUFFERING:
	System.out.println(msg);
	status.setBufferPercent (msg.parseBufferingPercent());
        setStatusVisible(true);
        break;
      case Message.STATE_CHANGED:
        if (msg.getSrc() == pipeline) {
	  int old, next;

	  old = msg.parseStateChangedOld();
	  next = msg.parseStateChangedNext();

          switch (next) {
	    case Element.PAUSE:
	      status.setMessage ("Paused");
	      status.setState(Status.STATE_PAUSED);
	      break;
	    case Element.PLAY:
	      status.setMessage ("Playing");
	      status.setState(Status.STATE_PLAYING);
	      setStatusVisible (false);
	      break;
	    case Element.STOP:
	      status.setMessage ("Stopped");
	      status.setState(Status.STATE_STOPPED);
	      setStatusVisible (true);
	      break;
	  }
	}
        break;
      default:
        break;
    }
  }

  public void newState(int aState) {
    int ret;
    switch (aState) {
      case Status.STATE_PAUSED:
	status.setMessage ("Pause");
        ret = pipeline.setState (Element.PAUSE);
	break;
      case Status.STATE_PLAYING:
	status.setMessage ("Play");
        ret = pipeline.setState (Element.PLAY);
	break;
      case Status.STATE_STOPPED:
	status.setMessage ("Stop");
        pipeline.setState (Element.STOP);
	break;
      default:
	break;
    }
  }

  public void newSeek(double aPos) {
    boolean res;
    com.fluendo.jst.Event event;

    /* get value, convert to PERCENT and construct seek event */
    event = com.fluendo.jst.Event.newSeek (Format.PERCENT, 
        (int)(aPos * 100.0 * Format.PERCENT_SCALE));

    /* send event to pipeline */
    res = pipeline.sendEvent(event);
    if (!res) {
      Debug.log(Debug.WARNING, "seek failed");
    }
  }
  
  public void start() 
  {
    int res;

    stopping = false;

    addMouseListener(this);
    addMouseMotionListener(this);
    status.addStatusListener(this);

    res = pipeline.setState (Element.PLAY);
    statusThread = new Thread(this);
    statusThread.start();
  }

  public void stop() {
    stopping = true;
    pipeline.setState (Element.STOP);
    try {
      if (statusThread != null)
        statusThread.interrupt();
    } catch (Exception e) { }
    try {
      if (statusThread != null)
        statusThread.join();
    } catch (Exception e) { }
    statusThread = null;
  }
}
