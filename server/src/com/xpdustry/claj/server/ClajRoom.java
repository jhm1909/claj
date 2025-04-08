package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.struct.IntMap;
import arc.util.serialization.Base64Coder;


public class ClajRoom implements NetListener {
  private boolean closed;
  
  public final long id;
  public final Connection host;
  /** Using IntMap instead of Seq for faster search */
  public final IntMap<Connection> clients = new IntMap<>();
  
  public ClajRoom(long id, Connection host) {
    this.id = id;
    this.host = host;
  }

  /** Alert the host that a new client is coming */
  @Override
  public void connected(Connection connection) {
    if (closed) return;
    
    // Assume the host is still connected
    ClajPackets.ConnectionJoinPacket p = new ClajPackets.ConnectionJoinPacket();
    p.conID = connection.getID();
    p.roomId = id;
    host.sendTCP(p);

    clients.put(connection.getID(), connection);
  }

  /** Alert the host that a client disconnected */
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) {
      close();
      return;
      
    } else if (host.isConnected()) {
      ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
      p.conID = connection.getID();
      p.reason = reason;
      host.sendTCP(p);
    }

    clients.remove(connection.getID());
  }
  
  /** Doesn't notify the room host about a disconnected client */
  public void disconnectedQuietly(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) close();
    else clients.remove(connection.getID());
  }
  
  @Override
  public void received(Connection connection, Object object) {
    if (closed) return;
    
    if (connection == host) {
      // Only claj packets are allowed in the host's connection
      // and can only be ConnectionPacketWrapPacket at this point.
      if (!(object instanceof ClajPackets.ConnectionPacketWrapPacket)) return;

      int conID = ((ClajPackets.ConnectionPacketWrapPacket)object).conID;
      Connection con = clients.get(conID);
      
      if (con != null && con.isConnected()) {
        boolean tcp = ((ClajPackets.ConnectionPacketWrapPacket)object).isTCP;
        Object o = ((ClajPackets.ConnectionPacketWrapPacket)object).buffer;
        
        if (tcp) con.sendTCP(o);
        else con.sendUDP(o);

      // Notify that this connection doesn't exist, this case normally never happen
      } else if (host.isConnected()) { 
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = conID;
        p.reason = DcReason.error;
        host.sendTCP(p);
      }
      
    } else if (host.isConnected() && clients.containsKey(connection.getID())) {
      // Only raw buffers are allowed here.
      // We never send claj packets to anyone other than the room host, framework packets are ignored
      // and mindustry packets are saved as raw buffer.
      if (!(object instanceof ByteBuffer)) return;
      
      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = connection.getID();
      p.buffer = (ByteBuffer)object;
      host.sendTCP(p);
    }
  }
  
  @Override
  public void idle(Connection connection) {
    if (closed) return;
    
    if (connection == host) {
      // Ignore if this is the room host
      
    } else if (host.isConnected() && clients.containsKey(connection.getID())) {
      ClajPackets.ConnectionIdlingPacket p = new ClajPackets.ConnectionIdlingPacket();
      p.conID = connection.getID();
      host.sendTCP(p);
    }
  }
  
  /** Notify the room id to the host. Must be called once. */
  public void create() {
    if (closed) return;
    
    // Assume the host is still connected
    ClajPackets.RoomLinkPacket p = new ClajPackets.RoomLinkPacket();
    p.roomId = id;
    host.sendTCP(p);
  }
  
  /** @return whether the room is closed or not */
  public boolean isClosed() {
    return closed;
  }
  
  /** 
   * Closes the room and disconnects the host and all clients. 
   * The room object shouldn't be used anymore after this.
   */
  public void close() {
    if (closed) return;
    closed = true; // close before kicking connections, to avoid receiving disconnect events
    
    host.close(DcReason.closed);
    clients.values().forEach(c -> c.close(DcReason.closed));
    clients.clear();
  }
  
  /** Checks if the connection is the room host or one of his client */
  public boolean contains(Connection con) {
    if (closed || con == null) return false;
    if (con == host) return true;
    return clients.containsKey(con.getID());
  }
  
  /** Send a message to the host and clients. */
  public void message(String text) {
    if (closed) return;
    
    // Just send to host, it will re-send it properly to all clients
    ClajPackets.ClajMessagePacket p = new ClajPackets.ClajMessagePacket();
    p.message = text;
    host.sendTCP(p);
  }

  /** 
   * Encode the room id in url-safe base64 string
   * @see com.xpdustry.claj.client.ClajLink
   */
  public String idToString() {
    return new String(Base64Coder.encode(longToBytes(id), Base64Coder.urlsafeMap));
  }
  
  
  /** Copy of {@link com.xpdustry.claj.client.ClajLink#longToBytes(long)} */
  private static byte[] longToBytes(long l) {
    byte[] result = new byte[Long.BYTES];
    for (int i=Long.BYTES-1; i>=0; i--) {
        result[i] = (byte)(l & 0xFF);
        l >>= 8;
    }
    return result;
  }
}
