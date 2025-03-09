package com.xpdustry.claj.client;

import arc.Events;
import arc.func.Cons;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.NetListener;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Threads;
import arc.util.Time;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.io.TypeIO;
import mindustry.net.Host;
import mindustry.net.ArcNetProvider.*;
import mindustry.net.Net.NetProvider;
import mindustry.Vars;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;


/** https://github.com/xzxADIxzx/Scheme-Size/blob/main/src/java/scheme/ClajIntegration.java */
public class CLaJ {
  public static final Seq<Client> clients = new Seq<>();
  public static NetListener serverListener;
  
  private static final ExecutorService worker = Threads.unboundedExecutor("CLaJ Worker");

  public static void init() {
    Events.run(HostEvent.class, CLaJ::clear);
    Events.run(ClientPreConnectEvent.class, CLaJ::clear);

    NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider"); // thanks

    arc.net.Server server = Reflect.get(provider, "server");
    serverListener = Reflect.get(server, "dispatchListener");
    
      
  }

  public static Client createRoom(String ip, int port, Cons<Link> done, Runnable disconnected) throws IOException {
    Client client = new Client(8192, 8192, new Serializer());
    Threads.daemon("CLaJ Room", client::run);

    client.addListener(new NetListener() {
      /** Used when creating redirectors. */
      public Link link;

      @Override
      public void connected(Connection connection) {
        client.sendTCP("new");
      }

      @Override
      public void disconnected(Connection connection, DcReason reason) {
        disconnected.run();
      }

      @Override
      public void received(Connection connection, Object object) {
        if (object instanceof String) {
          String message = (String)object;
          if (message.startsWith(Link.prefix)) done.get(link = new Link(message, ip, port));
          else if (message.equals("new")) createRedirector(link);
          else Call.sendMessage(message);
        }
      }
    });

    client.connect(5000, ip, port, port);
    clients.add(client);

    return client;
  }

  /** @return if the redirector was created successfully */
  public static boolean createRedirector(Link link) {
    if (link == null) return false;
    
    Client client = new Client(8192, 8192, new Serializer());
    Threads.daemon("CLaJ Redirector", client::run);

    client.addListener(serverListener);
    client.addListener(new NetListener() {
      @Override
      public void connected(Connection connection) {
        client.sendTCP("host" + link.keyWithPrefix());
      }
    });

    try { 
      client.connect(5000, link.host, link.port, link.port);
      clients.add(client);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  public static void joinRoom(Link link, Runnable success) {
    if (link == null) return;
    
    Vars.logic.reset();
    Vars.net.reset();

    Vars.netClient.beginConnecting();
    Vars.net.connect(link.host, link.port, () -> {
      if (!Vars.net.client()) return;

      ByteBuffer buffer = ByteBuffer.allocate(8192);
      buffer.put(Serializer.linkID);
      TypeIO.writeString(buffer, "join" + link.keyWithPrefix());

      buffer.limit(buffer.position()).position(0);
      Vars.net.send(buffer, true);
      
      success.run();
    });
  }
  
  public static void pingHost(String ip, int port, Cons<Long> success, Cons<Exception> failed) {
    worker.submit(() -> {
      try {
        
        try(DatagramSocket socket = new DatagramSocket()){
          long time = Time.millis();

          socket.send(new DatagramPacket(new byte[]{-2, 1}, 2, InetAddress.getByName(address), port));
          socket.setSoTimeout(2000);

          DatagramPacket packet = packetSupplier.get();
          socket.receive(packet);

          ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
          Host host = NetworkIO.readServerData((int)Time.timeSinceMillis(time), packet.getAddress().getHostAddress(), buffer);
          host.port = port;
          return host;
      }
        
      } catch (IOException e) { failed.get(e); }
      new FrameworkMessage.Ping();
    });
  }

  public static void clear() {
    clients.each(Client::close);
    clients.clear();
  }


  public static class Link {
    public static final String prefix = "CLaJ";
    public static final int prefixLength = prefix.length(), 
                            keyLength = 42;
    
    public final String key, host;
    public final int port;

    public Link(String key, String ip, int port) {
        this.key = key;
        this.host = ip;
        this.port = port;
    }
    
    public String keyWithPrefix() {
      return prefix + key;
    }
    
    @Override
    public String toString() {
      return prefix + key + '#' + host + ':' + port;
    }
    
    /** @return {@code null} if link is invalid */
    public static Link fromString(String link) {
      link = link.trim();
      if (!link.startsWith(prefix)) return null;

      int key = link.indexOf('#');
      if (key != keyLength + prefixLength) return null;

      int semicolon = link.indexOf(':');
      if (semicolon == -1 || semicolon == key+1) return null;

      int port = Strings.parseInt(link.substring(semicolon+1));
      if (port < 0 || port > 65535) return null;

      return new Link(link.substring(0, key), link.substring(key+1, semicolon), port);
    }
  }
  

  public static class Serializer extends PacketSerializer {
    public static final byte linkID = -3;

    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof String) {
        String link = (String)object;
        buffer.put(linkID);
        TypeIO.writeString(buffer, link);
      } else super.write(buffer, object);
    }

    @Override
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == linkID) return TypeIO.readString(buffer);

      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
  }
}
