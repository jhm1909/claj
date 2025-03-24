package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import com.xpdustry.claj.server.util.Strings;

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.FrameworkMessage.*;
import arc.net.NetListener;
import arc.net.NetSerializer;
import arc.net.Server;
import arc.struct.LongMap;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class ClajRelay extends Server implements NetListener {
  public final LongMap<ClajRoom> rooms = new LongMap<>();

  public ClajRelay() {
    super(32768, 8192, new Serializer());
    addListener(this);
  }

  @Override
  public void stop() {
    if (ClajConfig.warnClosing) {
      Log.info("Notifying that the server is closing...");
      
      try {
        // Notify all rooms that the server will be closed
        for (LongMap.Entry<ClajRoom> e : rooms) 
          e.value.message("The server is shutting down, please wait a minute or choose another server.");
        // Give time to message to be send to all clients
        Thread.sleep(2000);     
      } catch (Throwable ignored) {}
    }

    // Close rooms
    try { 
      for (LongMap.Entry<ClajRoom> e : rooms) 
        e.value.close();  
    } catch (Throwable ignored) {}
    rooms.clear();
    // Close server
    super.stop();
  }
  
  @Override
  public void connected(Connection connection) {
    if (ClajConfig.blacklist.contains(Strings.getIP(connection))) {
      connection.close(DcReason.closed);
      return;
    }
  
    Log.debug("Connection @ received.", Strings.conIDToString(connection));
    connection.setArbitraryData(new Ratekeeper());
  }
  
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    Log.debug("Connection @ lost: @.", Strings.conIDToString(connection), reason);
    
    // Avoid searching for a room if it was an invalid connection or just a ping
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;
    
    ClajRoom room = find(connection);
    
    if (room != null) {
      room.disconnected(connection, reason);
      // Remove the room if it was the host
      if (connection == room.host) {
        rooms.remove(room.id);
        Log.info("Room @ closed because the host has disconnected.", room.idToString());
      }
    }      
  }
  
  @Override
  public void received(Connection connection, Object object) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper) || (object instanceof FrameworkMessage)) return;
    Ratekeeper rate = (Ratekeeper)connection.getArbitraryData();
    ClajRoom room = find(connection);
    
    // Simple packet spam protection
    if (ClajConfig.spamLimit > 0 && !rate.allow(3000L, ClajConfig.spamLimit)) {
      rate.occurences = -ClajConfig.spamLimit; // reset to prevent message spam
      
      if (room != null && room.host == connection) {
        // hosts can spam packets when killing core and etc.
        Log.warn("Connection @ not disconnected for packets spamming, because it's a room host.", 
                 Strings.conIDToString(connection));
        return;
      }
       
      Log.warn("Connection @ disconnected for packets spamming.", Strings.conIDToString(connection));
      if (room != null) {
        room.message("A client has been kicked for packets spamming.");
        room.disconnected(connection, DcReason.closed);
      }
      connection.close(DcReason.closed);

    } else if (object instanceof String) {
      // Compatibility for the xzxADIxzx's version
      if (ClajConfig.warnDeprecated) {
        connection.sendTCP("[scarlet][[CLaJ Server]:[] Your CLaJ version is obsolete! "
                         + "Please update it by removing the 'scheme size' mod and installing the 'claj v2' mod, "
                         + "in the mod browser.");
        connection.close(DcReason.error);
      }
      
    } else if (object instanceof ClajPackets.RoomJoinPacket) {
      // Disconnect from a potential another room.
      if (room != null) room.disconnected(connection, DcReason.closed);
      
      long roomId = ((ClajPackets.RoomJoinPacket)object).roomId;
      room = get(roomId);
      
      if (room != null) {
        room.connected(connection);
        Log.info("Connection @ joined the room @.", Strings.conIDToString(connection), room.idToString());   
      } else connection.close(DcReason.error);

    } else if (object instanceof ClajPackets.RoomCreateRequestPacket) {
      // Ignore if the connection is already in a room or hold one
      if (room != null) return;
      
      room = new ClajRoom(newRoomId(), connection);
      rooms.put(room.id, room);
      room.create();
      Log.info("Room @ created by connection @.", room.idToString(), Strings.conIDToString(connection));
      
    } else if (object instanceof ClajPackets.RoomCloseRequestPacket) {
      // Ignore if not in a room or not the host
      if (room == null || room.host != connection) return;
      
      room.close();
      rooms.remove(room.id);
      Log.info("Room @ closed by connection @ (the host).", room.idToString(), Strings.conIDToString(connection));
      
    } else if (object instanceof ClajPackets.ConnectionClosedPacket) {
      // Only room host can request a connection closing
      if (room == null || room.host != connection) return;
      
      int conID = ((ClajPackets.ConnectionClosedPacket)object).conID;
      Connection con = arc.util.Structs.find(getConnections(), c -> c.getID() == conID);
      DcReason reason = ((ClajPackets.ConnectionClosedPacket)object).reason;
      
      if (con != null) {
        Log.info("Connection @ (the room host) closed the connection @.", Strings.conIDToString(connection), 
                 Strings.conIDToString(con));
        room.disconnectedQuietly(con, reason);
        con.close(reason);
      }
      
    // Ignore if the connection is not in a room
    } else if (room != null) {
      room.received(connection, object);
    }
  }
  
  @Override
  public void idle(Connection connection) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;

    ClajRoom room = find(connection);
    if (room != null) room.idle(connection);
  }
  
  public long newRoomId() {
    long id;
    /* re-roll if -1 because it's used to specify an uncreated room */ 
    do { id = arc.math.Mathf.rand.nextLong(); } 
    while (id == -1 || rooms.containsKey(id));
    return id;
  }
  
  public ClajRoom get(long roomId) {
    return rooms.get(roomId);
  }
  
  public ClajRoom find(Connection con) {
    for (LongMap.Entry<ClajRoom> e : rooms) {
      if (e.value.contains(con)) return e.value;
    }
    return null;
  }

  
  public static class Serializer implements NetSerializer {
    public ByteBuffer last = ByteBuffer.allocate(8192);
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ByteBuffer) {
        buffer.put((ByteBuffer)object);
          
      } else if (object instanceof FrameworkMessage) {
        buffer.put((byte)-2); //framework id
        writeFramework(buffer, (FrameworkMessage)object);
          
      } else if (object instanceof ClajPackets.Packet) {
        ClajPackets.Packet packet = (ClajPackets.Packet)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(packet));
        packet.write(new ByteBufferOutput(buffer));
        
      } else if (ClajConfig.warnDeprecated && (object instanceof String)) {
        buffer.put((byte)-3/*old claj version*/);
        try { new ByteBufferOutput(buffer).writeUTF((String)object); }
        catch (Exception e) { throw new RuntimeException(e); }
      }
    }

    @Override
    public Object read(ByteBuffer buffer) {
      byte id = buffer.get();

      if (id == -2/*framework id*/) return readFramework(buffer);
      if (ClajConfig.warnDeprecated && id == -3/*old claj version*/) {
        try { return new ByteBufferInput(buffer).readUTF(); }
        catch (Exception e) { throw new RuntimeException(e); }
      }
      if (id == ClajPackets.id) {
        ClajPackets.Packet packet = ClajPackets.newPacket(id);
        packet.read(new ByteBufferInput(buffer));
        return packet;
      }

      // Non-claj packets are saved as raw buffer, to avoid re-serialization
      last.clear();
      last.put(buffer.position(buffer.position()-1));
      last.limit(buffer.limit() - buffer.position());

      return last.position(0);
    }

    public void writeFramework(ByteBuffer buffer, FrameworkMessage message) {
      if (message instanceof Ping) {
        Ping ping = (Ping)message;
        buffer.put((byte) 0).putInt(ping.id).put(ping.isReply ? (byte)1 : 0);
      } else if (message instanceof DiscoverHost) buffer.put((byte)1);
      else if (message instanceof KeepAlive) buffer.put((byte)2);
      else if (message instanceof RegisterUDP) buffer.put((byte)3).putInt(((RegisterUDP)message).connectionID);
      else if (message instanceof RegisterTCP) buffer.put((byte)4).putInt(((RegisterTCP)message).connectionID);
    }

    public FrameworkMessage readFramework(ByteBuffer buffer) {
      byte id = buffer.get();

      if (id == 0) {
          Ping p = new Ping();
          p.id = buffer.getInt();
          p.isReply = buffer.get() == 1;
          return p;
      } else if (id == 1) {
          return FrameworkMessage.discoverHost;
      } else if (id == 2) {
          return FrameworkMessage.keepAlive;
      } else if (id == 3) {
          RegisterUDP p = new RegisterUDP();
          p.connectionID = buffer.getInt();
          return p;
      } else if (id == 4) {
          RegisterTCP p = new RegisterTCP();
          p.connectionID = buffer.getInt();
          return p;
      } else {
        throw new RuntimeException("Unknown framework message!");
      }
    }
  }
}
