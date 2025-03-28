package com.xpdustry.claj.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.struct.IntMap;
import arc.util.Ratekeeper;
import arc.util.Reflect;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;


public class ClajProxy extends Client implements NetListener {
  public static int defaultTimeout = 5000; //ms
  
  private final IntMap<VirtualConnection> connections = new IntMap<>();
  private final arc.net.Server server;
  private final NetListener serverDispatcher;
  private Cons<Long> roomCreated;
  private Runnable roomClosed;
  private long roomId = -1;
  private volatile boolean shutdown;
  
  /** No-op rate keeper, to avoid the player's server from life blacklisting the claj server . */
  private Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };

  public ClajProxy() {
    super(32768, 16384, new Serializer());
    addListener(this);
    
    mindustry.net.Net.NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");

    server = Reflect.get(provider, "server");
    //connections = Reflect.get(provider, "connections");
    serverDispatcher = Reflect.get(server, "dispatchListener");
  }

  /** This method must be used instead of others connect methods */
  public void connect(String host, int udpTcpPort, Cons<Long> roomCreatedCallback, 
                     Runnable roomClosedCallback) throws java.io.IOException {
    roomCreated = roomCreatedCallback;
    roomClosed = roomClosedCallback;
    connect(defaultTimeout, host, udpTcpPort, udpTcpPort);
  }
  
  /** redefine #run() and #stop() to handle exceptions and restart update loop if needed */
  @Override
  public void run() {
    shutdown = false;
    while(!shutdown) {
      try { update(250); } 
      catch (IOException ex) { close(); } 
      catch (ArcNetException ex) {
        if (roomId == -1) {
          close();
          Reflect.set(Client.class, this, "lastProtocolError", ex);
          throw ex;
        }
      }
    }
  }
  
  /** redefine #run() and #stop() to handle exceptions and restart update loop if needed */
  @Override
  public void stop() {
    if(shutdown) return;
    close();
    shutdown = true;
    // For me it's showing an error, but there is no error....
    Reflect.<java.nio.channels.Selector>get(Client.class, this, "selector").wakeup();
  }
  
  public long roomId() {
    return roomId;
  }
  
  public void closeRoom() {
    roomId = -1;
    sendTCP(new ClajPackets.RoomCloseRequestPacket());
    close();
  }

  @Override
  public void connected(Connection connection) {
    // Request the room link
    sendTCP(new ClajPackets.RoomCreateRequestPacket());
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    roomId = -1;
    if (roomClosed != null) roomClosed.run();
    // We cannot communicate with the server anymore, so close all connections
    // This throwing me an unresolved class error: Ljava/lang/Iterable$-CC;
    //connections.values().forEach(c -> c.closeFromProxy(reason));
    for (IntMap.Values<VirtualConnection> iter=connections.values(); iter.hasNext();)
      iter.next().closeFromProxy(reason);
    connections.clear();
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!(object instanceof ClajPackets.Packet)) return;

    else if (object instanceof ClajPackets.ClajMessagePacket) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " + ((ClajPackets.ClajMessagePacket)object).message);
    
    } else if (object instanceof ClajPackets.RoomLinkPacket) {
      // If this happen and the room id has already been received, it's probably a mitm attack =/.
      // But it's not alerting if the first id received is the wrong and right one is blocked by the middle-man.
      if (roomId == -1) {
        roomId = ((ClajPackets.RoomLinkPacket)object).roomId;
        // -1 is not allowed since it's used to specify an uncreated room
        if (roomId != -1 && roomCreated != null) roomCreated.get(roomId);
      } else Vars.ui.showInfo("@claj.proxy.alert.room-link-changed"); // alert the host about that

    } else if (object instanceof ClajPackets.ConnectionWrapperPacket) {
      // Ignore packets until the room id is received
      if (roomId == -1) return;
      
      int id = ((ClajPackets.ConnectionWrapperPacket)object).conID;
      VirtualConnection con = connections.get(id);
      
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

          connections.put(id, con = new VirtualConnection(this, id));
          con.notifyConnected0();
          // Change the packet rate and chat rate to a no-op version
          ((mindustry.net.NetConnection)con.getArbitraryData()).packetRate = noopRate;
          ((mindustry.net.NetConnection)con.getArbitraryData()).chatRate = noopRate;
        }

      } else if (object instanceof ClajPackets.ConnectionPacketWrapPacket) {
        Object o = ((ClajPackets.ConnectionPacketWrapPacket)object).object;
        con.notifyReceived0(o);

      } else if (object instanceof ClajPackets.ConnectionIdlingPacket) {
        con.notifyIdle0();

      } else if (object instanceof ClajPackets.ConnectionClosedPacket) {
        con.closeFromProxy(((ClajPackets.ConnectionClosedPacket)object).reason);
      }
    }
  }

  
  public static class Serializer extends mindustry.net.ArcNetProvider.PacketSerializer {
    @Override
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == ClajPackets.id) {
        ClajPackets.Packet p = ClajPackets.newPacket(buffer.get());
        p.read(new ByteBufferInput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket)  // This one is special
          ((ClajPackets.ConnectionPacketWrapPacket)p).object = super.read(buffer);
        return p;
      }

      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ClajPackets.Packet) {
        ClajPackets.Packet p = (ClajPackets.Packet)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(p));
        p.write(new ByteBufferOutput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket) // This one is special
          super.write(buffer, ((ClajPackets.ConnectionPacketWrapPacket)p).object);
        return;
      }

      super.write(buffer, object);
    }
  }
 
  
  /** We can safely remove some things,  only {@link #sendTCP(Object)} and {@link #sendUDP(Object)} are useful. */
  public static class VirtualConnection extends Connection {
    final int id;
    /** 
     * A virtual connection is always connected until we closing it, 
     * so the proxy will notify the server to close the connection in turn,
     * or when the server notifies that the connection has been closed.
     */
    volatile boolean isConnected = true;
    ClajProxy proxy;
    /** The server will notify if the client is idling */
    volatile boolean isIdling = false;
    
    public VirtualConnection(ClajProxy proxy, int id) {
      this.proxy = proxy;
      this.id = id;
      addListener(proxy.serverDispatcher);
    }
 
    @Override
    public int sendTCP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = true;
      p.object = object;
      return proxy.sendTCP(p);
    }

    @Override
    public int sendUDP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = false;
      p.object = object;
      return proxy.sendUDP(p);
    }

    @Override
    public void close(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = false;
      isIdling = false;
      if(wasConnected) {
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = id;
        p.reason = reason;
        proxy.sendTCP(p);
        proxy.connections.remove(id);
        notifyDisconnected0(reason);
      }
    }
    
    public void closeFromProxy(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = false;
      isIdling = false;
      if(wasConnected) {
        proxy.connections.remove(id);
        notifyDisconnected0(reason);
      }
    }
  
    @Override
    public int getID() { return id; }
    @Override
    public boolean isConnected() { return isConnected; }
    @Override
    public void setKeepAliveTCP(int keepAliveMillis) {}
    @Override
    public void setTimeout(int timeoutMillis) {}
    @Override
    public InetSocketAddress getRemoteAddressTCP() { return proxy.getRemoteAddressTCP(); }
    @Override
    public InetSocketAddress getRemoteAddressUDP() { return proxy.getRemoteAddressUDP(); }
    @Override
    public int getTcpWriteBufferSize() { return 0; } // never used
    @Override
    public boolean isIdle() { return isIdling; }
    @Override
    public void setIdleThreshold(float idleThreshold) {} // never used
    @Override
    public String toString() { return "Connection " + id; }
    
    public void notifyConnected0() {
      for(NetListener listener : getListeners())
        listener.connected(this);
    }
  
    public void notifyDisconnected0(DcReason reason) {
      for(NetListener listener : getListeners())
        listener.disconnected(this, reason);
    }
  
    public void notifyIdle0() {
      isIdling = true;
      for(NetListener listener : getListeners()) {
        listener.idle(this);
        if(!isIdle()) break;
      }
    }
    
    public void notifyReceived0(Object object) {
      for(NetListener listener : getListeners())
        listener.received(this, object);
    }
    
    private java.lang.reflect.Field listenersField;
    
    NetListener[] getListeners() {
      try {
        if (listenersField == null) {
          listenersField = Connection.class.getDeclaredField("listeners");
          listenersField.setAccessible(true);
        }
        return (NetListener[])listenersField.get(this);
        
      } catch (Exception e) { throw new RuntimeException(e); }
    }
  }
}
