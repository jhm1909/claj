package com.xpdustry.claj.client;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import arc.func.Cons;
import arc.net.Connection;
import arc.net.DcReason;
import arc.struct.Seq;
import arc.util.Reflect;

import mindustry.Vars;


public class ClajProxy extends arc.net.Client implements arc.net.NetListener {
  public static int defaultTimeout = 5000; //ms
  
  private final Seq<VirtualConnection> connections = new Seq<>();
  private final arc.net.Server server;
  private final arc.net.NetListener serverDispatcher;
  private Cons<Long> roomCreated;
  private Runnable roomClosed;
  private long roomId = -1;

  public ClajProxy() {
    super(8192, 16384, new Serializer());
    addListener(this);
    
    mindustry.net.Net.NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");

    server = Reflect.get(provider, "server");
    serverDispatcher = Reflect.get(server, "dispatchListener");
  }
  
  public long roomId() {
    return roomId;
  }
  
  /** This method must be used instead of others connect methods */
  public void connect(String host, int udpTcpPort, Cons<Long> roomCreatedCallback, 
                     Runnable roomClosedCallback) throws java.io.IOException {
    roomCreated = roomCreatedCallback;
    roomClosed = roomClosedCallback;
    connect(defaultTimeout, host, udpTcpPort, udpTcpPort);
  }
  
  /** This method must be used instead if {@link #close(DcReason)} */
  public void close() {
    //TODO: verify that
    sendTCP(new ClajPackets.RoomCloseRequestPacket());
    super.close();
  }

  public void connected(Connection connection) {
    // Request the room link
    sendTCP(new ClajPackets.RoomCreateRequestPacket());
  }

  public void disconnected(Connection connection, DcReason reason) {
    // We cannot communicate with the server anymore, so close all connections
    connections.each(c -> c.closeFromProxy(reason));
    connections.clear();
    if (roomClosed != null) roomClosed.run();
  }

  public void received(Connection connection, Object object) {
    // Only CLaJ Packets are allowed in the connection
    if (!(object instanceof ClajPackets.BasePacket)) {
      return;
    
    } else if (object instanceof ClajPackets.RoomLinkPacket) {
      // If this happen and the room id has already been received, it's probably a mitm attack =/.
      // But it's not alerting if the first id received is the wrong and right one is blocked by the middle-man.
      if (roomId == -1) {
        roomId = ((ClajPackets.RoomLinkPacket)object).roomId;
        if (roomCreated != null) roomCreated.get(roomId);
      } else Vars.ui.showInfo("@claj.proxy.alert.room-link-changed"); // alert the host about that

    } else if (object instanceof ClajPackets.ConnectionWrapperPacket) {
      int id = ((ClajPackets.ConnectionWrapperPacket)object).conID;
      VirtualConnection con = connections.find(c -> c.id == id);
      
      if (con == null) {
        // Create a new connection
        if (object instanceof ClajPackets.ConnectionJoinPacket) {
          // Check if the link is the right
          if (((ClajPackets.ConnectionJoinPacket)object).roomId != roomId) {
            ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
            p.conID = id;
            p.reason = DcReason.error;
            sendTCP(p);
            return;
          }
          
          connections.add(new VirtualConnection(this));
          // since the function to add a connection is private, we need to use reflection
          //Reflect.invoke(server, "addConnection", new Object[] {con}, Connection.class);
          // ^^^ Yeah no, we shouldn't add the connection, it will cause problems later
        }
        
      } else if (object instanceof ClajPackets.ConnectionsPacketWrapPacket) {
        serverDispatcher.received(con, ((ClajPackets.ConnectionsPacketWrapPacket)object).object);
        
      } else if (object instanceof ClajPackets.ConnectionIdlingPacket) {
        serverDispatcher.idle(con);  
        
      } else if (object instanceof ClajPackets.ConnectionClosedPacket) {
        con.closeFromProxy(((ClajPackets.ConnectionClosedPacket)object).reason);
        connections.remove(con);
      }
    }
  }

  
  public static class Serializer extends mindustry.net.ArcNetProvider.PacketSerializer {
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == ClajPackets.id) {
        ClajPackets.BasePacket p = ClajPackets.newPacket(buffer.get());
        p.read(buffer);
        if (p instanceof ClajPackets.ConnectionsPacketWrapPacket)  // This one is special
          ((ClajPackets.ConnectionsPacketWrapPacket)p).object = super.read(buffer);
        return p;
      }

      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
    
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ClajPackets.BasePacket) {
        ClajPackets.BasePacket p = (ClajPackets.BasePacket)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(p));
        p.write(buffer);
        if (p instanceof ClajPackets.ConnectionsPacketWrapPacket) // This one is special
          super.write(buffer, ((ClajPackets.ConnectionsPacketWrapPacket)p).object);
        return;
      }

      super.write(buffer, object);
    }
  }
 
  
  /** We can safely remove some things,  only {@link #sendTCP(Object)} and {@link #sendUDP(Object)} are useful. */
  public static class VirtualConnection extends Connection {
    int id = -1;
    /** 
     * A virtual connection is always connected until we closing it, 
     * so the proxy will notify the server to close the connection in turn,
     * or when the server notifies that the connection has been closed.
     */
    volatile boolean isConnected;
    ClajProxy proxy;
    
    public VirtualConnection(ClajProxy proxy) {
      this.proxy = proxy;
    }

    
    public int sendTCP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");

      ClajPackets.ConnectionsPacketWrapPacket packet = new ClajPackets.ConnectionsPacketWrapPacket();
      packet.conID = id;
      packet.object = object;
      return proxy.sendTCP(packet);
    }

    public int sendUDP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");

      ClajPackets.ConnectionsPacketWrapPacket packet = new ClajPackets.ConnectionsPacketWrapPacket();
      packet.conID = id;
      packet.object = object;
      return proxy.sendUDP(packet);
    }

    public void close(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = false;
      if(wasConnected) {
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = id;
        p.reason = reason;
        proxy.sendTCP(p);
        proxy.serverDispatcher.disconnected(this, reason);
        proxy.connections.remove(this);
      }
    }
    
    public void closeFromProxy(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = false;
      if(wasConnected) {
        proxy.serverDispatcher.disconnected(this, reason);
        //proxy.connections.remove(this);
      }
    }
  
    public int getID() { return id; }
    public boolean isConnected() { return isConnected; }
    public void setKeepAliveTCP(int keepAliveMillis) {}
    public void setTimeout(int timeoutMillis) {}
    public InetSocketAddress getRemoteAddressTCP() { return proxy.getRemoteAddressTCP(); }
    public InetSocketAddress getRemoteAddressUDP() { return proxy.getRemoteAddressUDP(); }
    public int getTcpWriteBufferSize() { return 0; } // never used
    public boolean isIdle() { return false; } // the server will notify if the client is idling
    public void setIdleThreshold(float idleThreshold) {} // never used
    public String toString() { return "Connection " + id; }
  }
}
