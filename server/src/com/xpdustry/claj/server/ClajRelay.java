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
import arc.struct.IntSet;
import arc.struct.LongMap;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;


public class ClajRelay extends Server implements NetListener {
  /** Cache already notified idling connection to avoid packet spamming */
  protected final IntSet notifiedIdle = new IntSet();
  public final LongMap<ClajRoom> rooms = new LongMap<>();

  public ClajRelay() {
    super(32768, 16384, new Serializer());
    addListener(this);
  }

  @Override
  public void stop() {
    if (ClajConfig.warnClosing && !rooms.isEmpty()) {
      Log.info("Notifying rooms that the server is closing...");
      
      try {
        // Notify all rooms that the server will be closed
        rooms.values().forEach(r -> 
          r.message("The server is shutting down, please wait a minute or choose another server."));  
      
        // Yea we needs a new thread... because we don't have arc.Timer
        Threads.daemon(() -> {
          // Give time to message to be send to all clients
          try { Thread.sleep(2000); }
          catch (InterruptedException ignored) {}
          closeRooms();
          super.stop();
        });
        return;
      } catch (Throwable ignored) {}
    }

    closeRooms();
    super.stop();
  }
  
  public void closeRooms() {
    try { rooms.values().forEach(ClajRoom::close); } 
    catch (Throwable ignored) {}
    rooms.clear();  
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
    notifiedIdle.remove(connection.getID());
    
    // Avoid searching for a room if it was an invalid connection or just a ping
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;
    
    ClajRoom room = find(connection);
    
    if (room != null) {
      room.disconnected(connection, reason);
      // Remove the room if it was the host
      if (connection == room.host) {
        rooms.remove(room.id);
        Log.info("Room @ closed because connection @ (the host) has disconnected.", 
                 room.idToString(), Strings.conIDToString(connection));
      } else Log.info("Connection @ left the room @.",  Strings.conIDToString(connection), room.idToString());  
    }      
  }
  
  @Override
  public void received(Connection connection, Object object) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper) || (object instanceof FrameworkMessage)) return;
    notifiedIdle.remove(connection.getID());
    Ratekeeper rate = (Ratekeeper)connection.getArbitraryData();
    ClajRoom room = find(connection);
    
    // Simple packet spam protection, ignored for room hosts
    if ((room == null || room.host != connection) && 
        ClajConfig.spamLimit > 0 && !rate.allow(3000L, ClajConfig.spamLimit)) {
      rate.occurences = -ClajConfig.spamLimit; // reset to prevent message spam

      if (room != null) {
        room.message("A client has been kicked for packets spamming.");
        room.disconnected(connection, DcReason.closed);
      }
      connection.close(DcReason.closed);   
      Log.warn("Connection @ disconnected for packets spamming.", Strings.conIDToString(connection));
      
    // Compatibility for the xzxADIxzx's version
    } else if ((object instanceof String) && ClajConfig.warnDeprecated) {
      connection.sendTCP("[scarlet][[CLaJ Server]:[] Your CLaJ version is obsolete! Please update it by "
                       + "installing the 'claj' mod, in the mod browser.");
      connection.close(DcReason.error);
      Log.warn("Rejected room creation of connection @ for incompatible version.", Strings.conIDToString(connection));
      
    } else if (object instanceof ClajPackets.RoomJoinPacket) {
      // Disconnect from a potential another room.
      if (room != null) {
        // Ignore if it's the host of another room
        if (room.host == connection) return;
        room.disconnected(connection, DcReason.closed);
      }

      room = get(((ClajPackets.RoomJoinPacket)object).roomId);
      
      if (room != null) {
        room.connected(connection);
        Log.info("Connection @ joined the room @.", Strings.conIDToString(connection), room.idToString());   
      } else connection.close(DcReason.error);

    } else if (object instanceof ClajPackets.RoomCreateRequestPacket) {
      // Check the version of client
      String version = ((ClajPackets.RoomCreateRequestPacket)object).version;
      // Ignore the last part of version, the minor part. (versioning format: 2.major.minor)
      // The minor part is used when no changes have been made to the client version.
      if (version == null || Strings.isVersionAtLeast(version, Main.serverVersion, 2)) {
        ClajPackets.ClajMessagePacket p = new ClajPackets.ClajMessagePacket();
        p.message = "Your CLaJ version is outdated, please update it by reinstalling the 'claj' mod.";
        connection.sendTCP(p);
        connection.close(DcReason.error);
        Log.warn("Rejected room creation of connection @ for outdated version.", Strings.conIDToString(connection));
        return;
      }
      
      // Ignore if the connection is already in a room or hold one
      if (room != null) return;

      room = new ClajRoom(newRoomId(), connection);
      rooms.put(room.id, room);
      room.create();
      Log.info("Room @ created by connection @.", room.idToString(), Strings.conIDToString(connection));  

    } else if (object instanceof ClajPackets.RoomCloseRequestPacket) {
      // Only room host can close the room
      if (room == null || room.host != connection) return;

      rooms.remove(room.id);
      room.close();
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
      if (room.host == connection && (object instanceof ClajPackets.ConnectionWrapperPacket))
        notifiedIdle.remove(((ClajPackets.ConnectionWrapperPacket)object).conID);
      
      room.received(connection, object);
    }
  }
  
  @Override
  public void idle(Connection connection) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;
    if (!notifiedIdle.add(connection.getID())) return;

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
    for (ClajRoom r : rooms.values()) {
      if (r.contains(con)) return r;
    }
    return null;  
  }

  
  public static class Serializer implements NetSerializer {
    /** Since there are only one thread using the serializer, it's not necessary to use a thread-local variable. */
    private ByteBuffer last = ByteBuffer.allocate(16384);
    
    @Override
    public Object read(ByteBuffer buffer) {
      byte id = buffer.get();

      if (id == -2/*framework id*/) return readFramework(buffer);
      if (id == -3/*old claj version*/ && ClajConfig.warnDeprecated) {
        try { return new ByteBufferInput(buffer).readUTF(); }
        catch (Exception e) { throw new RuntimeException(e); }
      }
      if (id == ClajPackets.id) {
        ClajPackets.Packet packet = ClajPackets.newPacket(buffer.get());
        packet.read(new ByteBufferInput(buffer));
        if (packet instanceof ClajPackets.ConnectionPacketWrapPacket)
          ((ClajPackets.ConnectionPacketWrapPacket)packet).buffer = last.clear().put(buffer).flip();
        return packet;
      }

      // Non-claj packets are saved as raw buffer, to avoid re-serialization
      return last.clear().put(buffer.position(buffer.position()-1)).flip();
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ByteBuffer) {
        buffer.put((ByteBuffer)object);
          
      } else if (object instanceof FrameworkMessage) {
        buffer.put((byte)-2); //framework id
        writeFramework(buffer, (FrameworkMessage)object);
        
      } else if ((object instanceof String) && ClajConfig.warnDeprecated) {
        buffer.put((byte)-3/*old claj version*/);
        try { new ByteBufferOutput(buffer).writeUTF((String)object); }
        catch (Exception e) { throw new RuntimeException(e); }          
        
      } else if (object instanceof ClajPackets.Packet) {
        ClajPackets.Packet packet = (ClajPackets.Packet)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(packet));
        packet.write(new ByteBufferOutput(buffer));
        if (packet instanceof ClajPackets.ConnectionPacketWrapPacket)
          buffer.put(((ClajPackets.ConnectionPacketWrapPacket)packet).buffer);
      }
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
