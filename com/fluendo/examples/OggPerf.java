package com.fluendo.examples;

import com.fluendo.utils.*;

import java.io.*;
import java.util.*;
import com.jcraft.jogg.*;

public class OggPerf  
{
  private static final int BUFFSIZE = 8192;

  private InputStream inputStream;

  private SyncState oy;
  private Vector streams;
  private boolean stopping;

  class OggStream {
    public int serialno;
    public StreamState os;
    public boolean bos;

    public OggStream (int serial) {
      serialno = serial;
      os = new StreamState();
      os.init(serial);
      os.reset();
      bos = true;
    }
  }

  public OggPerf (InputStream is) {
    inputStream = is;

    oy = new SyncState();
    streams = new Vector();
    stopping = false;
  }

  public static void main(String[] args) {
    try {
      int run = 10;

      long start = System.currentTimeMillis();
      while (run-- > 0) {
        OggPerf perf = new OggPerf(new FileInputStream(args[0]));

        perf.start();
      }
      long end = System.currentTimeMillis();

      System.out.println("ellapsed: "+(end-start));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void start() {
    int res;
    Page og;
    Packet op;

    System.out.println("started ogg reader");

    og = new Page();
    op = new Packet();

    try {
      while (!stopping) {
        int index = oy.buffer(BUFFSIZE);
        //System.out.println("reading "+index+" "+BUFFSIZE);
        int read = inputStream.read(oy.data, index, BUFFSIZE);
        //System.out.println("read "+read);
        if (read < 0)
          break;
        oy.wrote(read);
  
        while (!stopping) {
	  res = oy.pageout(og);
	  //System.out.println("pageout "+res);
          if (res == 0)
	    break; // need more data
          if(res == -1) { 
	    // missing or corrupt data at this page position
            // no reason to complain; already complained above
          }
          else {
	    int serial = og.serialno();
	    OggStream stream = null;
	    for (int i=0; i<streams.size(); i++) {
	      stream = (OggStream) streams.elementAt(i);
	      if (stream.serialno == serial)
	        break;
	      stream = null;
	    }
	    if (stream == null) {
  	      System.out.println("new stream "+serial);
	      stream = new OggStream(serial);
	      streams.addElement(stream);
	    }

	    //System.out.println("pagein");
            res = stream.os.pagein(og);
            if (res < 0) {
              // error; stream version mismatch perhaps
              System.err.println("Error reading first page of Ogg bitstream data.");
              return;
            }
	    while (!stopping) {
	      res = stream.os.packetout(op);
	      //System.out.println("packetout "+res);
              if(res == 0)
	        break; // need more data
              if(res == -1) { 
	        // missing or corrupt data at this page position
                // no reason to complain; already complained above
              }
              else {
                // we have a packet.  Decode it
	        if (stream.bos) {
	          // typefind
	          if (op.packet_base[op.packet+1] == 0x76) {
		    System.out.println ("found vorbis audio");
	          }
	          else if (op.packet_base[op.packet+1] == 0x73) {
		    System.out.println ("found smoke video");
	          }
	          else if (op.packet_base[op.packet+1] == 0x74) {
		    System.out.println ("found theora video");
		  }
	          stream.bos = false;
	        }
	      }
  	    }
	  }
	}
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      stopping = true;
    }
    
    System.out.println("ogg reader done");
  }

  public void stop() {
    stopping = true;
  }
}
