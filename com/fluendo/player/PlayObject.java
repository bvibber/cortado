package com.fluendo.player;

import com.fluendo.utils.*;

import java.awt.*;
import java.io.*;
import java.util.*;
import com.jcraft.jogg.*;

public class PlayObject {
  private InputStream inputStream;
  private Component component;
  private Thread thread;

  public PlayObject() {
  }

  public void setInputStream(InputStream is) {
    inputStream = is;
  }
  public InputStream getInputStream() {
    return inputStream;
  }

  public Component getComponent() {
    return component;
  }

  public void stop() {
  }
  public void pause() {
  }
  public void play() {
    Cortado pt = new Cortado();
  }
}
