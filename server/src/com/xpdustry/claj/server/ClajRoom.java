package com.xpdustry.claj.server;

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

  @Override
  public void connected(Connection connection) {
    if (closed) return;
    
    // Alert the host that a new client is coming
    ClajPackets.ConnectionJoinPacket p = new ClajPackets.ConnectionJoinPacket();
    p.conID = connection.getID();
    p.roomId = id;
    host.sendTCP(p);
    
    synchronized (clients) {
      clients.put(connection.getID(), connection);
    }
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) {
      close();
      return;
    }
    
    ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
    p.conID = connection.getID();
    p.reason = reason;
    host.sendTCP(p);
    
    synchronized (clients) {
      clients.remove(connection.getID());
    }
  }
  
  /** Doesn't notify the room host about a disconnected client */
  public void disconnectedQuietly(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) {
      close();
      return;
    }

    synchronized (clients) {
      clients.remove(connection.getID());
    }
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
      
      if (con != null) {
        boolean tcp = ((ClajPackets.ConnectionPacketWrapPacket)object).isTCP;
        Object o = ((ClajPackets.ConnectionPacketWrapPacket)object).object;
        
        if (tcp) con.sendTCP(o);
        else con.sendUDP(o);

      } else { // Notify that this connection doesn't exist, this case normally never happen
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = conID;
        p.reason = DcReason.error;
        host.sendTCP(p);
      }
      
    } else {
      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = connection.getID();
      p.object = object;
      
      host.sendTCP(p);
    }
  }
  
  @Override
  public void idle(Connection connection) {
    if (closed) return;
    
    if (connection == host) {
      // Ignore if this is the room host
      
    } else {
      ClajPackets.ConnectionIdlingPacket p = new ClajPackets.ConnectionIdlingPacket();
      p.conID = connection.getID();
      host.sendTCP(p);
    }
  }
  
  /** Notify the room id to the host. Must be called once. */
  public void create() {
    ClajPackets.RoomLinkPacket p = new ClajPackets.RoomLinkPacket();
    p.roomId = id;
    host.sendTCP(p);
  }
  
  /** @return whether the room is closed or not */
  public boolean isClosed() {
    return closed;
  }
  
  /** Closes the room and disconnects the host and all clients. The room object should not be used after this. */
  public void close() {
    if (closed) return;
    
    host.close(DcReason.closed);
    synchronized (clients) {
      for (IntMap.Entry<Connection> e : clients)
        e.value.close(DcReason.closed);
      clients.clear();  
    }
    
    closed = true;
  }
  
  /** Checks if the connection is the room host or one of his client */
  public boolean contains(Connection con) {
    if (con == null) return false;
    if (con == host) return true;
    synchronized (clients) {
      return clients.containsKey(con.getID());  
    }
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
