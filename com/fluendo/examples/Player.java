package com.fluendo.examples;

import com.fluendo.player.*;
import java.awt.*;
import java.io.*;

public class Player {
  public static void main(String args[]) {
    Cortado c = new Cortado();

    Frame f = new Frame();

    f.setSize(100,100);
    f.add(c);
    f.show();

    c.init();
    c.setUrl (args[0]);
    c.setLocal (true);
    c.start();
  }
}
