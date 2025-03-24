package com.xpdustry.claj.server;

import arc.func.Prov;
import arc.net.DcReason;
import arc.struct.ArrayMap;
import arc.util.ArcRuntimeException;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


/** @implNote This class must be the same for the client and the server. */
public class ClajPackets {
  /** Identifier for CLaJ packets */
  public static final byte id = -4; /*doesn't uses old claj packet identifier to avoid problems*/
  
  protected static final ArrayMap<Class<?>, Prov<? extends Packet>> packets = new ArrayMap<>();
  
  static {
    register(ConnectionPacketWrapPacket::new);
    register(ConnectionClosedPacket::new);
    register(ConnectionJoinPacket::new);
    register(ConnectionIdlingPacket::new);
    register(RoomCreateRequestPacket::new);
    register(RoomCloseRequestPacket::new);
    register(RoomLinkPacket::new);
    register(RoomJoinPacket::new);
    register(ClajMessagePacket::new);
  }

  
  public static <T extends Packet> void register(Prov<T> cons) {
    packets.put(cons.get().getClass(), cons);
  }

  public static byte getId(Packet packet) {
    int id = packets.indexOfKey(packet.getClass());
    if(id == -1) throw new ArcRuntimeException("Unknown packet type: " + packet.getClass());
    return (byte)id;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Packet> T newPacket(byte id) {
    if (id < 0 || id >= packets.size) throw new ArcRuntimeException("Unknown packet id: " + id);
    return ((Prov<T>)packets.getValueAt(id)).get();
  }

  /****************************/
  
  public static abstract class Packet {
    public void read(ByteBufferInput read) {};
    public void write(ByteBufferOutput write) {};
  }
  
  public static abstract class ConnectionWrapperPacket extends Packet {
    public int conID = -1;
 
    public void read(ByteBufferInput read) {
      conID = read.readInt();
      read0(read);
    }
    
    public void write(ByteBufferOutput write) {
      write.writeInt(conID);
      write0(write);
    }
    
    public void read0(ByteBufferInput read) {};
    public void write0(ByteBufferOutput write) {};
  }

  /** Special packet for connection packet wrapping. */
  public static class ConnectionPacketWrapPacket extends ConnectionWrapperPacket {
    /** serialization will be done by the proxy */
    public Object object;
    public boolean isTCP;
    
    public void read0(ByteBufferInput read) {
      isTCP = read.readBoolean();
    }
    
    public void write0(ByteBufferOutput write) {
      write.writeBoolean(isTCP);
    }
  }
  
  public static class ConnectionClosedPacket extends ConnectionWrapperPacket {
    private static DcReason[] reasons = DcReason.values();

    public DcReason reason;

    public void read0(ByteBufferInput read) {
      byte b = read.readByte();
      reason = b < 0 || b >= reasons.length ? DcReason.error : reasons[b];
    }

    public void write0(ByteBufferOutput write) {
      write.writeByte((byte)reason.ordinal());
    }
  }
  
  public static class ConnectionJoinPacket extends ConnectionWrapperPacket {
    public long roomId = -1;
    
    public void read0(ByteBufferInput read) {
      roomId = read.readLong();
    }
    
    public void write0(ByteBufferOutput write) {
      write.writeLong(roomId);
    }
  }
  
  public static class ConnectionIdlingPacket extends ConnectionWrapperPacket {
  }
  
  public static class RoomCreateRequestPacket extends Packet {
  }
  
  public static class RoomCloseRequestPacket extends Packet {
  }
  
  public static class RoomLinkPacket extends Packet {
    public long roomId = -1;
    
    public void read(ByteBufferInput read) {
      roomId = read.readLong();
    }
    
    public void write(ByteBufferOutput write) {
      write.writeLong(roomId);
    }
  }
  
  public static class RoomJoinPacket extends RoomLinkPacket {
  }
  
  public static class ClajMessagePacket extends Packet {
    public String message;
    
    public void read(ByteBufferInput read) {
      try { message = read.readUTF(); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
    
    public void write(ByteBufferOutput write) {
      try { write.writeUTF(message); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
  }
}
