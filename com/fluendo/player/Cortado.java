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
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Cortado extends Applet implements ImageTarget,
                PreBufferNotify,
                Runnable, 
		MouseMotionListener,
		MouseListener
{
  private static Cortado cortado;

  private String urlString;
  private boolean local;
  private double framerate;
  private boolean audio;
  private boolean video;
  private boolean keepAspect;
  private int bufferSize;
  private String userId;
  private String password;
  private boolean usePrebuffer;
  private PreBuffer preBuffer;
  private int bufferLow;
  private int bufferHigh;
  private int debug;

  private double aspect;

  private Image image;
  private ImageProducer imageProd;
  private Thread videoThread;
  private Thread audioThread;
  private Thread mainThread;
  private Thread statusThread;
  private DataConsumer videoConsumer;
  private DataConsumer audioConsumer;
  private Demuxer demuxer;

  private InputStream is;
  private Clock clock;
  private boolean havePreroll;
  private Status status;
  private PopupMenu menu;
  private boolean stopping;
  private Hashtable params = new Hashtable();
  private Configure configure;

  private boolean useDb = true;
  private Dimension dbSize;
  private Image dbImage;
  private Graphics dbGraphics;
  private Dimension appletDimension;


  private boolean needRepaint;

  public String getAppletInfo() {
    return "Title: Fluendo media player \nAuthor: Wim Taymans \nA Java based network multimedia player.";
  }

  public String[][] getParameterInfo() {
    String[][] info = {
      {"url",         "URL",     "The media file to play"},
      {"local",       "boolean", "Is this a local file (default false)"},
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
  
  public void init() {
    cortado = this;

    image = null;
    aspect = 0.0;
    is = null;
    clock = null;
    preBuffer = null;

    urlString = getParam("url", null);
    local = String.valueOf(getParam("local", "false")).equals("true");
    framerate = Double.valueOf(getParam("framerate", "5.0")).doubleValue();
    audio = String.valueOf(getParam("audio","true")).equals("true");
    video = String.valueOf(getParam("video","true")).equals("true");
    keepAspect = String.valueOf(getParam("keepAspect","true")).equals("true");
    usePrebuffer = String.valueOf(getParam("preBuffer","true")).equals("true");
    useDb = String.valueOf(getParam("doubleBuffer","true")).equals("true");
    bufferSize = Integer.valueOf(getParam("bufferSize","200")).intValue();
    bufferLow = Integer.valueOf(getParam("bufferLow","10")).intValue();
    bufferHigh = Integer.valueOf(getParam("bufferHigh","70")).intValue();
    debug = Integer.valueOf(getParam("debug","3")).intValue();
    userId = getParam("userId",  null);
    password = getParam("password",  null);
    configure = new Configure();
    Debug.level = debug;
    Debug.log(Debug.WARNING, "build info: " + configure.buildInfo);

    needRepaint = true;

    /* FIXME: this needs to be redone in resize callbacks */
    appletDimension = getSize();

    setBackground(Color.black);
    setForeground(Color.white);

    status = new Status(this);
    status.setVisible(true);
    status.setHaveAudio (audio);

    menu = new PopupMenu();
    menu.add("About...");
    this.add (menu);
  }

  public Component getComponent() {
    return this;
  }

  public synchronized void update(Graphics g) {
    if (!needRepaint) {
      return;
    }

    if (useDb) {
      if (appletDimension == null) {
        appletDimension = getSize();
      }
      boolean makeImage =
        dbImage == null ||
        dbSize.height != appletDimension.height ||
        dbSize.width != appletDimension.width;

      if (makeImage)
      {
        dbSize = appletDimension;
        dbImage = createImage (dbSize.width, dbSize.height);
        dbGraphics = dbImage.getGraphics();
      }
      dbGraphics.setColor (getBackground() );
      dbGraphics.fillRect (0, 0, dbSize.width, dbSize.height);
      dbGraphics.setColor (Color.black);
      dbGraphics.setFont (getFont() );
      paint (dbGraphics);
      g.drawImage (dbImage, 0, 0, null);
    }
    else {
      paint(g);
    }
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
        if (preBuffer != null) {
          int percent = (preBuffer.getFilled() * 100) /
	           (1024 * bufferSize);

          if (status.isVisible()) {
            status.setBufferPercent(percent);
            forceRepaint();
	  }
	  preBuffer.dumpStats();
	  QueueManager.dumpStats();
	}

        Thread.sleep(500);
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
    if (appletDimension == null) {
      appletDimension = getSize();
    }
    int dwidth = appletDimension.width;
    int dheight = appletDimension.height;
    int x = 0, y = 0;
    int width = dwidth;
    
    if (image != null) {
      int height = dheight;

      /* need to get the image dimension or else the image
         will not draw for some reason */
      int imgW = image.getWidth(this);
      int imgH = image.getHeight(this);

      if (keepAspect) {
	double aspectSrc = (((double)imgW) / imgH) * aspect;

	height = (int) (width / aspectSrc);
	if (height > dheight) {
	  height = dheight;
	  width = (int) (height * aspectSrc);
	}
      }
      x = (dwidth - width) / 2;
      y = (dheight - height) / 2;

      if (status.isVisible()) {
        g.setClip(x, y, width, dheight-12-y);
        g.drawImage(image, x, y, width, height, null); 
        g.setClip(0, 0, dwidth, dheight);
      }
      else {
        g.drawImage(image, x, y, width, height, null); 
        g.setColor(Color.black);
	int pos = Math.max (y+height, dheight-12);
        g.fillRect(x, pos, x+width, dheight);
      }
      image.flush();
    }
    if (status != null && status.isVisible()) {
      status.setBounds(x, dheight-12, width, 12);
      status.paint(g);
    }
    needRepaint = false;
  }

  private synchronized void forceRepaint() {
    needRepaint = true;
    /* we call update in this thread, it's not nice but it
     * improves smoothness and CPU usage */
    //update(getGraphics());
    repaint();
  }

  public synchronized void setImage(Object obj, double framerate, double aspect) {
    if (obj instanceof Image) {
      setImage ((Image) obj, framerate, aspect);
    }
    else if (obj instanceof ImageProducer) {
      setImage ((ImageProducer) obj, framerate, aspect);
    }
  }

  public synchronized void setImage(ImageProducer prod, double framerate, double aspect) {
    if (!needRepaint) {
      if (imageProd != prod) {
        image = createImage (prod);
	imageProd = prod;
      }
      setImage (image, framerate, aspect);
    }
  }
  public synchronized void setImage(Image newImage, double framerate, double aspect) 
  {
    if (!needRepaint) {
      image = newImage;
      this.framerate = framerate;
      this.aspect = aspect;
      if (!havePreroll) {
  	int dwidth = appletDimension.width;
   	int dheight = appletDimension.height;
        getGraphics().clearRect(0, 0, dwidth, dheight);
        status.setMessage("Buffering...");
      }
      forceRepaint();
    }
  }

  public void preBufferNotify (int state) {
    String str = null;

    synchronized (preBuffer) {
      if (!havePreroll && state != STATE_BUFFER) {
        return;
      }
      switch (state) {
        case PreBufferNotify.STATE_BUFFER:
          str = "Buffering...";
	  status.setVisible(true);
  	  clock.pause();
	  break;
        case PreBufferNotify.STATE_PLAYBACK:
          str = "Playing...";
	  clock.play();
	  status.setVisible(false);
	  break;
        case PreBufferNotify.STATE_OVERFLOW:
	  clock.play();
	  break;
        default:
	  break;
      }
    }
    if (str == null)
      return;

    status.setMessage(str);
    forceRepaint();
  }

  public void mouseClicked(MouseEvent e){}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) 
  {
    status.setVisible(false);
  }
  public void mousePressed(MouseEvent e) 
  {
    if (e.getButton() == MouseEvent.BUTTON3) {
      menu.show(this, e.getX(), e.getY());
    }
  }
  public void mouseReleased(MouseEvent e) 
  {
  }

  public void mouseDragged(MouseEvent e){}
  public void mouseMoved(MouseEvent e)
  {
    if (status != null) {
      if (e.getY() > appletDimension.height-12) {
        status.setVisible(true);
      }
      else {
        status.setVisible(false);
      }
    }
  }

  public void start() 
  {
    stopping = false;
    Plugin plugin = null;

    status.setMessage("Opening "+urlString+"...");
    try {
      try {
        if (local) {
          Debug.log(Debug.INFO, "reading from file "+urlString);
          is = new FileInputStream (urlString);
        }
        else {
          Debug.log(Debug.INFO, "reading from url "+urlString);
          URL url = new URL(urlString);
          Debug.log(Debug.INFO, "trying to open "+url);
  	  URLConnection uc = url.openConnection();
	  if (userId != null && password != null) {
	    String userPassword = userId + ":" + password;
	    String encoding = Base64Converter.encode (userPassword.getBytes());
	    uc.setRequestProperty ("Authorization", "Basic " + encoding);
	  }
	  String mime = uc.getContentType();
	  if (mime == null) {
            mime = "application/ogg";
	    Debug.log(Debug.INFO, "could not get mime type, using: "+mime);
	  }
	  int extraPos = mime.indexOf(';');
          if (extraPos != -1) {
	    mime = mime.substring(0, extraPos);
  	  }
	  Debug.log(Debug.INFO, "got stream mime: "+mime);
	  plugin = Plugin.makeByMime(mime);
	  if (plugin == null) {
            status.setMessage("Unknown stream "+urlString+"...");
            return;
	  }
          is = uc.getInputStream();
          Debug.log(Debug.INFO, "opened "+url);
        }
      }
      catch (SecurityException e) {
        e.printStackTrace();
        status.setMessage("Not allowed "+urlString+"...");
        return;
      }
      catch (Exception e) {
        e.printStackTrace();
        status.setMessage("Failed opening "+urlString+"...");
        return;
      }
      status.setMessage("Loading media...");

      clock = new Clock();
      QueueManager.reset();
      addMouseMotionListener(this);
      addMouseListener(this);

      if (video) {
        videoConsumer = new VideoConsumer(clock, this, framerate);
        videoThread = new Thread(videoConsumer);
      }
      if (audio) {
        try {
	  Class.forName("javax.sound.sampled.AudioSystem");
	  Debug.log(Debug.INFO, "using high quality javax.sound.* as audio backend");
          audioConsumer = new AudioConsumer(clock);
	}
	catch (ClassNotFoundException e) {
	  Debug.log(Debug.INFO, "using low quality sun.audio.* as audio backend");
          audioConsumer = new AudioConsumerSun(clock);
	}
        audioThread = new Thread(audioConsumer);
	//clock.setProvider(audioConsumer);
      }

      if (plugin == null) {
        plugin = Plugin.makeByMime("application/ogg");
      }

      InputStream dis;
      if (usePrebuffer) {
        preBuffer = new PreBuffer (is, 1024 * bufferSize, bufferLow, bufferHigh, this);
        dis = preBuffer;
      }
      else {
        dis = is;
      }
      demuxer = new Demuxer(dis, plugin, this, audioConsumer, videoConsumer);
      mainThread = new Thread(demuxer);

      statusThread = new Thread(this);
      statusThread.start();

      if (audio) {
        audioThread.start();
      }
      if (video) {
        videoThread.start();
      }

      try {
        synchronized (Thread.currentThread()) {
          mainThread.start();
        }

        synchronized (clock) {
          boolean ready;

          havePreroll = false;
          Debug.log(Debug.INFO, "waiting for preroll...");
          do {
	    ready = true;
	    if (video) {
	      ready &= videoConsumer.isReady();
	    }
	    if (audio) {
	      ready &= audioConsumer.isReady();
	    }
	    if (!ready) {
              clock.wait(100);	
	    }
          } while (!ready);
        }
        havePreroll = true;

        long timeBase = 0;
	if (video) {
	  timeBase = videoConsumer.getQueuedTime();
	  Debug.log(Debug.INFO, "video timeBase: "+timeBase);
	}
	if (audio) {
	  timeBase = audioConsumer.getQueuedTime();
	  Debug.log(Debug.INFO, "audio timeBase: "+timeBase);
	}
	clock.updateAdjust(timeBase);
	
        if (preBuffer != null) {
          synchronized (preBuffer) {
            Debug.log(Debug.INFO, "consumers ready");
  	    Debug.log(Debug.INFO, "preroll done, starting prebuffer...");
            status.setHavePercent (usePrebuffer);
	    preBuffer.startBuffer();
	    if (preBuffer.isFilled()) {
	      clock.play();
	    }
	    else {
	      Debug.log(Debug.INFO, "not buffered, not starting yet "+preBuffer.getFilled());
	    }
	  }
        }
        else {
          Debug.log(Debug.INFO, "consumers ready");
  	  Debug.log(Debug.INFO, "preroll done, starting...");
	  status.setVisible(false);
	  status.setMessage("Playing...");
  	  clock.play();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
      status.setMessage("Failed opening "+urlString+"...");
      stop();
    }
  }

  private final void interruptThread (Thread t) 
  {
    try {
      if (t != null)
        t.interrupt();
    } catch (Exception e) { }
  }

  private final void joinThread (Thread t) 
  {
    try {
      if (t != null)
        t.join();
    } catch (Exception e) { }
  }

  public void stop() {
    if (demuxer != null)
      demuxer.stop();

    try {
      stopping = true;
      if (preBuffer != null)
        preBuffer.stop();
      if (video && videoConsumer != null)
        videoConsumer.stop();
      if (audio && audioConsumer != null)
        audioConsumer.stop();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    if (video)
      interruptThread (videoThread);
    if (audio)
      interruptThread (audioThread);
    interruptThread (mainThread);
    interruptThread (statusThread);

    try {
      if (is != null)
        is.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    if (video)
      joinThread(videoThread);
    if (audio)
      joinThread(audioThread);
    joinThread(mainThread);
    joinThread(statusThread);
  }
}
