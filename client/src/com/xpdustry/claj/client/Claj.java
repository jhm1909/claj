package com.xpdustry.claj.client;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import arc.Events;
import arc.func.Cons;
import arc.net.Client;
import arc.util.Time;

import mindustry.Vars;
import mindustry.game.EventType;


public class Claj {
  static {
    Events.run(EventType.HostEvent.class, Claj::closeRoom);
    Events.run(EventType.ClientPreConnectEvent.class, Claj::closeRoom);
    Events.run(EventType.DisposeEvent.class, () -> {
      disposeRoom();
      disposePinger();
    });
  } 
  
  private static ClajProxy room;
  private static Client pinger;
  private static ExecutorService worker = arc.util.Threads.unboundedExecutor("CLaJ Worker", 1);
  private static arc.net.NetSerializer tmpSerializer;
  private static ByteBuffer tmpBuffer = ByteBuffer.allocate(16);// we only need 10 bytes for the room join packet
  private static Object roomLock = new Object(), pingerLock = new Object();

  public static boolean isRoomClosed() {
    return room == null || !room.isConnected();
  }

  /** @apiNote async operation */
  public static void createRoom(String ip, int port, Cons<ClajLink> done, Runnable disconnected) {
    if (room == null) worker.submit(room = new ClajProxy());
    
    worker.submit(() -> {
      try {
        if (room.isConnected()) throw new IllegalStateException("Room is already created, please close it before.");
        synchronized (roomLock) { 
          room.connect(ip, port, id -> done.get(new ClajLink(ip, port, id)), disconnected);
        }
      } catch (Throwable e) { Vars.net.handleException(e); }  
      finally { Vars.ui.loadfrag.hide(); }
    });
  }
  
  /** Just close the room connection, doesn't delete it */
  public static void closeRoom() {
    if (room != null) {
      synchronized (roomLock) {
        room.close();
      }
    }
  }
  
  /** Delete properly the room */
  public static void disposeRoom() {
    if (room != null) {
      synchronized (roomLock) {
        room.close();
        room.stop();
        try { room.dispose(); }
        catch (Exception ignored) {}
        room = null;
      }
    }
  }

  public static void joinRoom(ClajLink link, Runnable success) {
    if (link == null) return;
    
    Vars.logic.reset();
    Vars.net.reset();

    Vars.netClient.beginConnecting();
    Vars.net.connect(link.host, link.port, () -> {
      if (!Vars.net.client()) return;
      if (tmpSerializer == null) tmpSerializer = new ClajProxy.Serializer();
      
      // We need to serialize the packet manually
      tmpBuffer.clear();
      ClajPackets.RoomJoinPacket p = new ClajPackets.RoomJoinPacket();
      p.roomId = link.roomId;
      tmpSerializer.write(tmpBuffer, p);
      tmpBuffer.limit(tmpBuffer.position()).position(0);
      Vars.net.send(tmpBuffer, true);
      
      success.run();
    });
  }

  /** @apiNote async operation but blocking new tasks if a ping is already in progress */
  public static void pingHost(String ip, int port, Cons<Long> success, Cons<Exception> failed) {
    if (tmpSerializer == null) tmpSerializer = new ClajProxy.Serializer();
    if (pinger == null) worker.submit(pinger = new Client(8192, 8192, tmpSerializer));
    
    worker.submit(() -> {
      // Connect successfully is enough.
      synchronized (pingerLock) {
        long time = Time.millis();
        try { 
          pinger.connect(2000, ip, port); 
          time = Time.timeSinceMillis(time);
          pinger.close();
          success.get(time);
        } catch (Exception e) { failed.get(e); }
      }
    });
  }
  
  public static void disposePinger() {
    if (pinger != null) {
      synchronized (pingerLock) {
        pinger.close();
        pinger.stop();
        try { pinger.dispose(); }
        catch (Exception ignored) {}
        pinger = null;
      }
    }
  }
}
