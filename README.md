# Copy Link and Join (CLaJ)
This system allow you to play with your friends just by creating a room, copy the link and send it to your friends. <br>
In fact it's pretty much the same thing as Hamachi, but in a Mindustry mod.

This is a bundled, reworked and optimized version of the [CLaJ server](https://github.com/xzxADIxzx/Copy-Link-and-Join) and the [xzxADIxzx's Scheme-Size mod](https://github.com/xzxADIxzx/Scheme-Size) (with only the CLaJ feature).

> [!IMPORTANT]
> This CLaJ version is not compatible with the [xzxADIxzx's](https://github.com/xzxADIxzx) one. <br>
> The protocol has been reworked and optimized and CLaJ links have also been changed to a more standard version.


## Mindustry v8 note
Mindustry v8 has been released, and many changes have been made. Mods must now make changes to be compatible with this version.

The mod will not be updated to this version at this time, but it remains compatible with it. <br>
For that, you must makes a trick to force the minimal mindustry version. <br>
But please note that after this, the mod will no longer be available for v7. You'll need to reinstall the mod to cancel the changes.
<details>
  <summary>Steps to modify the mod for Mindustry v8 compatibility:</summary>
  
  First, open the mods folder. You can open it from the ``Mods`` menu, click on any mod you have, and then click ``Open Folder``. After that close your game. <br>
  Look for a file named ``claj-client.jar``, ``xpdustryclaj.zip`` or ``xpdustryclaj-v2.zip``, and open it with an archive tool like 7-zip, or extract the file. <br>
  Next, edit the file ``mod.hjson`` by replacing the line ``minGameVersion`` with ``minGameVersion: "147"``. *(for 7-zip: ``Right Click`` and ``Edit``)* <br>
  Then save the file, close it, accept changes for 7-zip, or re-zipping it. <br>
  If all goes well, when you restart your game (Mindustry v8 version), the mod will be available.
  
  ---

  Another method is to clone the repository, replace the line ``minGameVersion`` with ``minGameVersion: "147"`` in the file ``mod.hjson``, compile the mod *(see [#how-to-build](#how-to-build))*, and then copy the jar file into the mod directory.
</details>


## How to use
### Client
**First, if you don't have the mod yet, you can find it in the mod browser by searching for 'claj' and then installing it.**

Start and host a map as normal (or your campaign): ``Host Multiplayer Game`` **>** ``Host``. <br>
Then go to ``Manage CLaJ Room``, select a server (or add your own), now ``Create Room`` and wait for the room to be created, click ``Copy Link`` and send the copied link to your friends.

To join, it's simple, copy the link your friend sent you, open your game, go to ``Play`` **>** ``Join Game`` **>** ``Join via CLaJ``, paste the link and ``OK``.

Now, if all goods, you can play with your friends, so enjoy =).


### Server
To host a server, just run the command ``java -jar claj-server.jar <port>``, where ``<port>`` is the port for the server. <br>
Also don't forget to open the port in TCP and UDP mode on your end-point and redirect it to the host machine.

A CLaJ server doesn't need much memory and cpu, 256MB of memory and one core are enough, even at high traffic.<br>
To change the memory allocated to the server, change the command to ``java -Xms<memory> -Xmx<memory> -jar claj-server.jar <port>``, where ``<memory>`` is the memory allocated to the server *(e.g. 256m for 256 MB of ram)*.

> [!IMPORTANT]
> Please note that if you plan to make a public server, CLaJ servers are high bandwidth consumers, as they act as a relay. For an average server, around 1TB up/down of consumption per month and around 1MB/s of constant network usage.
>
> Also, you can create a Pull-Request to add your server to the public server list (in [public-servers.hjson](https://github.com/xpdustry/claj/blob/main/public-servers.hjson)).


## How it works
CLaJ is a system like [Hamachi](https://vpn.net/), that allows you to create a room and share the link to your friends. This way, they can connect to you as if they were on your private network.

The only differences are that Hamachi requires account creation and therefore the collection of personal information, etc., while CLaJ does not. And CLaJ is directly integrated into Mindustry and optimized for it, which makes it easier to use compared to Hamachi, which needs to stay in parallel of the game.

On the host player's side, it's server never receives packets from people connected via CLaJ, the work is done by the CLaJ Proxy which simply run the server's callbacks.


## How to build
Pre-build releases can be found in the [releases section](https://github.com/Xpdustry/claj/releases), but if you want to build the project yourself, follow the steps above.

To build the client version, simply run the command ``./gradlew client:build``. The jar file will be located in the root directory and named ``claj-client.jar``.

To build the server version, simply run the command ``./gradlew server:build``. The jar file will be located in the root directory and named ``claj-server.jar``.
