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
import java.awt.event.*;
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Cortado extends Applet implements ImageTarget, PreBufferNotify, Runnable, 
		MouseMotionListener,
		MouseListener
{
  private String urlString;
  private boolean local = false;
  private double framerate = 0.;
  private double aspect = 0.;
  private String userId;
  private String password;

  private Image image = null;
  private Thread videoThread;
  private Thread audioThread;
  private Thread mainThread;
  private Thread statusThread;
  private VideoConsumer videoConsumer;
  private AudioConsumer audioConsumer;
  private Demuxer demuxer;
  private boolean audio = true;
  private boolean video = true;
  private Object tick;
  private long startTime;
  private boolean keepAspect = true;
  private PreBuffer preBuffer;
  private InputStream is;
  private Clock clock;
  private boolean havePreroll;
  private Status status;
  private PopupMenu menu;
  private boolean stopping;

  /* Prebuffer in K */
  private int bufferSize = 200;
  
  public void init() {

    System.out.println("reading applet properties");

    try {
      urlString = getParameter("url");
      local = String.valueOf(getParameter("local")).equals("true");
      framerate = Double.valueOf(getParameter("framerate")).doubleValue();
      audio = !String.valueOf(getParameter("audio")).equals("false");
      video = !String.valueOf(getParameter("video")).equals("false");
      keepAspect = String.valueOf(getParameter("keepAspect")).equals("true");
      bufferSize = Integer.valueOf(getParameter("bufferSize")).intValue();
      if (bufferSize == 0) {
        bufferSize = 100;
      }
      userId = getParameter("userId");
      password = getParameter("password");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    setBackground(Color.black);

    status = new Status(this);
    status.setVisible(true);

    menu = new PopupMenu();
    menu.add("About...");
    this.add (menu);
  }

  public void setUrl (String url) {
    urlString = url;
  }
  public void setLocal (boolean local) {
    this.local = local;
  }
  public void setFramerate (double framerate) {
    this.framerate = framerate;
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void run() {
    System.out.println("entering status thread");
    while (!stopping) {
      try {
        int percent = (preBuffer.getFilled() * 100) /
	           (1024 * bufferSize);

        status.setBufferPercent(percent);
	repaint();

        Thread.currentThread().sleep(500);
      }
      catch (Exception e) {
        if (!stopping)
          e.printStackTrace();
      }
    }
    System.out.println("exit status thread");
  }

  public void paint(Graphics g) {
    Dimension d = getSize();
    int dwidth = d.width;
    int dheight = d.height;
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
        g.setClip(x, y, width, height-12);
        g.drawImage(image, x, y, width, height, null); 
        g.setClip(0, 0, dwidth, dheight);
      }
      else {
        //System.out.println("draw image "+image);
        g.drawImage(image, x, y, width, height, null); 
      }
    }
    if (status != null && status.isVisible()) {
      status.setBounds(x, dheight-12, width, 12);
      status.paint(g);
    }
  }

  public void setImage(Image newImage, double framerate, double aspect) {
    //System.out.println("set image "+newImage);
    if (image != newImage) {
      image = newImage;
      this.framerate = framerate;
      this.aspect = aspect;
      if (!havePreroll) {
        getGraphics().clearRect(0,0,getWidth(), getHeight());
        status.setMessage("Buffering...");
      }
      repaint((long)(1000/(framerate * 2)));
    }
  }

  public Component getComponent() {
    return this;
  }

  public void preBufferNotify (int state) {
    String str = null;

    synchronized (preBuffer) {
      if (!havePreroll && state == STATE_PLAYBACK) {
        System.out.println("no preroll yet, not starting");
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
    repaint();
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
      if (e.getY() > getHeight()-12) {
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
    repaint();
    //System.out.println("entering the start method");
    try {
      if (local) {
        System.out.println("reading from file "+urlString);
        is = new FileInputStream (urlString);
      }
      else {
        System.out.println("reading from url "+urlString);
        URL url = new URL(urlString);
        //URL url = new URL(getCodeBase(), urlString);
        System.out.println("trying to open "+url);
	URLConnection uc = url.openConnection();
	if (userId != null && password != null) {
	  String userPassword = userId + ":" + password;
	  String encoding = Base64Converter.encode (userPassword.getBytes());
	  uc.setRequestProperty ("Authorization", "Basic " + encoding);
	}
	String mime = uc.getContentType();
	int extraPos = mime.indexOf(';');
        if (extraPos != -1) {
	  mime = mime.substring(0, extraPos);
	}
	System.out.println ("got stream mime: "+mime);
	plugin = Plugin.makeByMime(mime);
	if (plugin == null) {
          status.setMessage("Unknown stream "+urlString+"...");
          repaint();
          return;
	}
        is = uc.getInputStream();
        System.out.println("opened "+url);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      status.setMessage("Failed opening "+urlString+"...");
      repaint();
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
      audioConsumer = new AudioConsumer(clock);
      audioThread = new Thread(audioConsumer);
    }

    preBuffer = new PreBuffer (is, 1024 * bufferSize, this);
    if (plugin == null) {
      plugin = Plugin.makeByMime("application/ogg");
    }
    demuxer = new Demuxer(preBuffer, plugin, this, audioConsumer, videoConsumer);
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
        System.out.println("waiting for preroll...");
        do {
	  ready = true;
	  if (video) {
	    ready &= videoConsumer.isReady();
	  }
	  if (audio) {
	    ready &= audioConsumer.isReady();
	  }
	  if (!ready) {
	    synchronized (this) {
              clock.wait(100);	
	    }
	  }
        } while (!ready);
      }
      synchronized (preBuffer) {
        System.out.println("consumers ready");
	System.out.println("preroll done, starting...");
	preBuffer.startBuffer();
	havePreroll = true;
	if (preBuffer.isFilled()) {
	  clock.play();
	}
	else {
	  System.out.println("not buffered, not starting yet "+preBuffer.getFilled());
	}
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void stop() {
    demuxer.stop();
    try {
      stopping = true;
      preBuffer.stop();
      if (video)
        videoConsumer.stop();
      if (audio)
        audioConsumer.stop();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      if (video)
        videoThread.interrupt();
      if (audio)
        audioThread.interrupt();
      mainThread.interrupt();
      statusThread.interrupt();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      is.close();
      if (video)
        videoThread.join();
      if (audio)
        audioThread.join();
      mainThread.join();
      statusThread.join();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

}
