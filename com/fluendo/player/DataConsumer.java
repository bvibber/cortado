package com.fluendo.player;

public interface DataConsumer
{
  public void consume(byte[] bytes, int offset, int len);
}
