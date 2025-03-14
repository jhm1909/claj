package com.xpdustry.claj.server;

import java.nio.ByteBuffer;


import arc.func.Prov;
import arc.net.DcReason;
import arc.struct.ArrayMap;
import arc.util.ArcRuntimeException;


/** @implNote This class must be the same for the client and the server. */
public class ClajPackets {
  /** Identifier for CLaJ packets */
  public static final byte id = -3;
  
  protected static final ArrayMap<Class<?>, Prov<? extends BasePacket>> packets = new ArrayMap<>();
  
  static {
    register(ConnectionsPacketWrapPacket::new);
    register(ConnectionClosedPacket::new);
    register(ConnectionJoinPacket::new);
    register(ConnectionIdlingPacket::new);
    register(RoomCreateRequestPacket::new);
    register(RoomCloseRequestPacket::new);
    register(RoomLinkPacket::new);
    register(RoomJoinPacket::new);
  }

  
  public static <T extends BasePacket> void register(Prov<T> cons) {
    packets.put(cons.get().getClass(), cons);
  }

  public static byte getId(BasePacket packet) {
    int id = packets.indexOfKey(packet.getClass());
    if(id == -1) throw new ArcRuntimeException("Unknown packet type: " + packet.getClass());
    return (byte)id;
  }

  @SuppressWarnings("unchecked")
  public static <T extends BasePacket> T newPacket(byte id) {
    if (id < 0 || id >= packets.size) throw new ArcRuntimeException("Unknown packet id: " + id);
    return ((Prov<T>)packets.getValueAt(id)).get();
  }

  
  public static abstract class BasePacket {
    public static final byte id = -3;

    public void read(ByteBuffer read) {};
    public void write(ByteBuffer write) {};
  }
  
  public static abstract class ConnectionWrapperPacket extends BasePacket {
    public int conID = -1;
 
    public void read(ByteBuffer read) {
      conID = read.getInt();
      read0(read);
    }
    
    public void write(ByteBuffer write) {
      write.putInt(conID);
      write0(write);
    }
    
    public void read0(ByteBuffer read) {};
    public void write0(ByteBuffer write) {};
  }

  /** Special packet for connection packet wrapping. Read/Write operations will be done at serialization */
  public static class ConnectionsPacketWrapPacket extends ConnectionWrapperPacket {
    public Object object;
  }
  
  public static class ConnectionClosedPacket extends ConnectionWrapperPacket {
    private static DcReason[] reasons = DcReason.values();

    public DcReason reason;

    public void read0(ByteBuffer read) {
      byte b = read.get();
      reason = b < 0 || b >= reasons.length ? DcReason.error : reasons[b];
    }

    public void write0(ByteBuffer write) {
      write.put((byte)reason.ordinal());
    }
  }
  
  public static class ConnectionJoinPacket extends ConnectionWrapperPacket {
    public long roomId = -1;
    
    public void read0(ByteBuffer read) {
      roomId = read.getLong();
    }
    
    public void write0(ByteBuffer write) {
      write.putLong(roomId);
    }
  }
  
  public static class ConnectionIdlingPacket extends ConnectionWrapperPacket {
  }
  
  public static class RoomCreateRequestPacket extends BasePacket {
  }
  
  public static class RoomCloseRequestPacket extends BasePacket {
  }
  
  public static class RoomLinkPacket extends BasePacket {
    public long roomId = -1;
    
    public void read(ByteBuffer read) {
      roomId = read.getLong();
    }
    
    public void write(ByteBuffer write) {
      write.putLong(roomId);
    }
  }
  
  public static class RoomJoinPacket extends BasePacket {
    public long roomId = -1;
    
    public void read(ByteBuffer read) {
      roomId = read.getLong();
    }
    
    public void write(ByteBuffer write) {
      write.putLong(roomId);
    }
  }
}
