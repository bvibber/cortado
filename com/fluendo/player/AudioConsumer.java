package com.fluendo.player;

import sun.audio.*;
import java.io.*;

import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;
import com.fluendo.utils.*;

public class AudioConsumer implements Runnable, DataConsumer
{
  private int queueid;
  private AudioStream as;
  private boolean ready;
  private Clock clock;
  private static final int MAX_BUFFER = Integer.MAX_VALUE;
  //private static final int MAX_BUFFER = 20;
  private boolean stopping = false;
  private long start;
  private long prev;
  private static final long DEVICE_BUFFER_TIME = 8 * 1024 / 8;
  private String currentType;
  private int packet = 0;

  private Info vi;
  private Comment vc;
  private DspState vd;
  private Block vb;

  private Packet op;
  private float[][][] _pcmf = new float[1][][];
  private int[] _index;

  private static final byte[] header = 
                         { 0x2e, 0x73, 0x6e, 0x64, 		// header in be
                           0x00, 0x00, 0x00, 0x18,              // offset
                           0x7f, 0xff-256, 0xff-256, 0xff-256,  // length
			   0x00, 0x00, 0x00, 0x01,		// ulaw
			   0x00, 0x00, 0x1f, 0x40, 		// frequency
			   0x00, 0x00, 0x00, 0x01		// channels
			 };
  private static final boolean ZEROTRAP=true;
  private static final short BIAS=0x84;
  private static final int CLIP=32635;
  private static final byte[] exp_lut = 
    { 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
      4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

  
  class MyIS extends InputStream
  {
    private byte[] current;
    private int pos;
    private int sampleCount;
     
    public MyIS() {
      current = header;
      pos = 0;
    }

    public int available () throws IOException {
      //System.out.println("******** available ");  
      return super.available();
    }

    public int read() {
      int res;

      if (stopping)
        return -1;

      if (current == null) {
        current = (byte[]) QueueManager.dequeue(queueid);
	pos = 0;
      }
      res = current[pos];
      if (res < 0) 
        res += 256;

      pos++;

      if (pos >= current.length) {
	current = null;
      }
      if (sampleCount == 0) {
        try {
	  // first sample, wait for signal
	  synchronized (clock) {
	    ready = true;
	    System.out.println("audio preroll wait");
	    clock.wait();
	    System.out.println("audio preroll go!");
	    clock.updateAdjust(-DEVICE_BUFFER_TIME);
	  }
	}
	catch (Exception e) {
	  e.printStackTrace();
	}
      }
      if (sampleCount % 8000 == 7999) {
        long sampleTime = (sampleCount+1)/8;
	long clockTime = clock.getMediaTime();
	long diff = clockTime + DEVICE_BUFFER_TIME + 500 - sampleTime;

	long absDiff = Math.abs(diff);
	long maxDiff = (30 * DEVICE_BUFFER_TIME) / 100;

	long adjust = (long)(Math.log(absDiff - maxDiff) * 5);
	if (diff > 0) {
	  clock.updateAdjust(-adjust);
	}
	else if (diff < 0) {
	  clock.updateAdjust(adjust);
	}
        /*System.out.println("sync: clock="+clockTime+
	                        " sampleTime="+sampleTime+
	                        " diff="+diff+
			        " adjust="+clock.getAdjust());  
				*/
	//QueueManager.dumpStats();
      }
      sampleCount++;

      return res;
    }
    public int read(byte[] bytes) throws IOException {
      //System.out.println("******** read "+bytes.length);  
      return super.read(bytes);
    }
    public int read(byte[] bytes, int offset, int len) throws IOException {
      //System.out.println("******** read "+offset+" "+len);  
      int read = super.read(bytes, offset, len);
      return read;
    }
  }

  public AudioConsumer(Clock newClock) {
    queueid = QueueManager.registerQueue(MAX_BUFFER);
    clock = newClock;
    vi = new Info();
    vc = new Comment();
    vd = new DspState();
    vb = new Block(vd);

    vi.init();
    vc.init();
      
    op = new Packet();
  }

  public boolean isReady() {
    return ready;
  }

  public void stop() {
    stopping = true;
  }

  public void run() {
    try {
      System.out.println("entering audio thread");
      start = System.currentTimeMillis();
      as = new AudioStream(new MyIS());
      start = System.currentTimeMillis();
      AudioPlayer.player.start(as);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setType (String type) {
    currentType = type;
  }

  public void consume(byte[] data, int offset, int len) {
    if (currentType.equals("audio/x-mulaw")) {
      byte[] bytes = new byte[len];
      System.arraycopy(data, offset, bytes, 0, len);
      QueueManager.enqueue(queueid, bytes);
    }
    else if (currentType.equals("audio/x-vorbis")) {
      //System.out.println ("creating packet");
      op.packet_base = data;
      op.packet = offset;
      op.bytes = len;
      op.b_o_s = (packet == 0 ? 1 : 0);
      op.e_o_s = 0;
      op.packetno = packet;

      if (packet < 3) {
        //System.out.println ("decoding header");
        if(vi.synthesis_headerin(vc, op) < 0){
	  // error case; not a vorbis header
	  System.err.println("This Ogg bitstream does not contain Vorbis audio data.");
	  return;
	}
        if (packet == 2) {
	  vd.synthesis_init(vi);
	  vb.init(vd);

          _index =new int[vi.channels];
	}
      }
      else {
        int samples;
        if (vb.synthesis(op) == 0) { // test for success!
          vd.synthesis_blockin(vb);
        }
        else {
          System.out.println ("decoding error");
        }
        //System.out.println ("decode vorbis done");
        while ((samples = vd.synthesis_pcmout (_pcmf, _index)) > 0) {
          float[][] pcmf=_pcmf[0];
  	  int target = 8000 * samples / vi.rate;
          byte[] bytes = new byte[target];

	  //System.out.println(vi.rate + " " +target+ " " +samples);

          for (int j=0; j<target; j++){
	    float val = 0.0f;

            for (int i=0; i<vi.channels; i++) {
	      val += pcmf[i][_index[i]+(vi.rate * j / 8000)];
	    }
	    val /= vi.channels;

            int sample = (int) (val * 32768);
	    int sign, exponent, mantissa, ulawbyte;

   	    if (sample>32767) sample=32767;
	    else if (sample<-32768) sample=-32768;
	    /* Get the sample into sign-magnitude. */
            sign = (sample >> 8) & 0x80;    /* set aside the sign */
	    if (sign != 0) sample = -sample;    /* get magnitude */
            if (sample > CLIP) sample = CLIP;    /* clip the magnitude */

            /* Convert from 16 bit linear to ulaw. */
            sample = sample + BIAS;
            exponent = exp_lut[(sample >> 7) & 0xFF];
            mantissa = (sample >> (exponent + 3)) & 0x0F;
            ulawbyte = ~(sign | (exponent << 4) | mantissa);
            if (ZEROTRAP)
              if (ulawbyte == 0) ulawbyte = 0x02;  /* optional CCITT trap */

	    bytes[j] = (byte)ulawbyte;
          }
          QueueManager.enqueue(queueid, bytes);
          //System.out.println ("decoded "+samples+" samples");
          vd.synthesis_read(samples);
        }
      }
      packet++;
    }
    else {
      System.out.println ("unkown type");
    }
  }
}
