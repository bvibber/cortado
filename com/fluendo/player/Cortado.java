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
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Cortado extends Applet implements ImageTarget
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
  private VideoConsumer videoConsumer;
  private AudioConsumer audioConsumer;
  private OggReader reader;
  private boolean audio = true;
  private boolean video = true;
  private Object tick;
  private long startTime;
  private boolean keepAspect = true;
  private InputStream is;
  
  public void init() {

    System.out.println("reading applet properties");

    try {
      urlString = getParameter("url");
      local = String.valueOf(getParameter("local")).equals("true");
      framerate = Double.valueOf(getParameter("framerate")).doubleValue();
      audio = !String.valueOf(getParameter("audio")).equals("false");
      video = !String.valueOf(getParameter("video")).equals("false");
      keepAspect = String.valueOf(getParameter("keepAspect")).equals("true");
      userId = getParameter("userId");
      password = getParameter("password");
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    setBackground(Color.black);
  }

  public void setUrl (String url) {
    urlString = url;
  }
  public void setLocal (boolean local) {
    this.local = local;
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void paint(Graphics g) {
    if (image != null) {
      Dimension d = getSize();
      int width = d.width;
      int height = d.height;

      /* need to get the image dimension or else the image
         will not draw for some reason */
      int imgW = image.getWidth(this);
      int imgH = image.getHeight(this);

      if (keepAspect) {
	double aspectSrc = (((double)imgW) / imgH) * aspect;

	height = (int) (width / aspectSrc);
	if (height > d.height) {
	  height = d.height;
	  width = (int) (height * aspectSrc);
	}
      }
      int x = (d.width - width) / 2;
      int y = (d.height - height) / 2;

      //System.out.println("draw image "+image);
      g.drawImage(image, x, y, width, height, null);
    }
  }

  public void setImage(Image newImage, double framerate, double aspect) {
    //System.out.println("set image "+newImage);
    if (image != newImage) {
      image = newImage;
      this.framerate = framerate;
      this.aspect = aspect;
      repaint((long)(1000/(framerate * 2)));
    }
  }

  public Component getComponent() {
    return this;
  }

  public void start() {
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
        is = uc.getInputStream();
        System.out.println("opened "+url);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      return;
    }

    Clock clock = new Clock();
    QueueManager.reset();

    if (video) {
      System.out.println("creating video consumer");
      videoConsumer = new VideoConsumer(clock, this, framerate);
      videoThread = new Thread(videoConsumer);
    }
    if (audio) {
      System.out.println("creating audio consumer");
      audioConsumer = new AudioConsumer(clock);
      audioThread = new Thread(audioConsumer);
    }

    System.out.println("creating main thread");
    reader = new OggReader(is, audioConsumer, videoConsumer);
    mainThread = new Thread(reader);

    if (audio) {
      System.out.println("starting audio thread");
      audioThread.start();
    }
    if (video) {
      System.out.println("starting video thread");
      videoThread.start();
    }

    try {
      synchronized (Thread.currentThread()) {
      System.out.println("starting main thread");
      mainThread.start();
      System.out.println("started main thread");
      }

      synchronized (clock) {
        boolean ready;

        System.out.println("waiting for preroll...");
        do {
	  ready = true;
	  if (video) {
            //System.out.println("polling videoconsumer");
	    ready &= videoConsumer.isReady();
	  }
	  if (audio) {
            //System.out.println("polling audioconsumer");
	    ready &= audioConsumer.isReady();
	  }
	  if (!ready) {
            //System.out.println("waiting 100 msec");
	    synchronized (this) {
              //System.out.println("in sync");
              clock.wait(100);	
              //System.out.println("done sync");
	    }
            //System.out.println("done waiting 100 msec");
	  }
        } while (!ready);

        System.out.println("consumers ready");
	startTime = System.currentTimeMillis();
	
        clock.notifyAll();
	System.out.println("preroll done, starting...");
	clock.setStartTime(startTime);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void stop() {
    reader.stop();
    try {
      is.close();
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
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      if (video)
        videoThread.join();
      if (audio)
        audioThread.join();
      mainThread.join();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

}
