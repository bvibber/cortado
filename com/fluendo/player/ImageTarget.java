package com.fluendo.player;

import java.awt.*;

public interface ImageTarget {
  public Component getComponent();
  public void setImage (Image image, double framerate, double aspect);
}
