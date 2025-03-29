# Copy Link and Join v2 (CLaJ v2)
This system allow you to play with your friends just by creating a room, copy the link, send it to your friends. <br>
In fact it's pretty much the same thing as Hamachi, but in a Mindustry mod.

This is a bundled, reworked and optimized version of the [CLaJ server](https://github.com/xzxADIxzx/Copy-Link-and-Join) and the [xzxADIxzx's Scheme-Size mod](https://github.com/xzxADIxzx/Scheme-Size), with only the CLaJ feature.

> [!IMPORTANT]
> The CLaJ version 2 is not compatible with the older one. <br>
> The protocol has been reworked and optimized, and CLaJ links have also been changed to a more standard version.

## How to use
### Client
**First, if you don't have the mod yet, you can find it in the mod browser by searching for 'claj' and then installing it.**

Start and host a map as normal (or your campaign): ``Host multiplayer game`` **>** ``Host``. <br>
Then go to ``Manage CLaJ room``, select a server (or add your own), after ``Create room``, wait for the room to be created, click ``Copy link`` and send the copied link to your friends.

To join, it's simple, copy the link your friend sent you, open your game, go to ``Play`` **>** ``Join Game`` **>** ``Join via CLaJ``, paste the link and ``OK``. <br>
Now, if all goods, you can play with your friends, so enjoy =).


### Server
To host a server, just run the command ``java -jar claj-v2-server.jar <port>``, where ``<port>`` is the port for the server. <br>
Also don't forget to open the port in TCP and UDP mode on your end-point and redirect it to the host machine.

A CLaJ server doesn't need much memory and cpu, 256MB of memory and one core are enough, even at high traffic.<br>
To change the memory allocated to the server, change the command to ``java -Xms<memory> -Xmx<memory> -jar claj-v2-server.jar <port>``, where ``<memory>`` is the memory allocated to the server *(e.g. 256m for 256 MB of ram)*.

> [!IMPORTANT]
> Please note that, if you plan to make a public server, you can create a Pull-Request to add it to the public server list, in [public-servers.json](https://github.com/xpdustry/claj-v2/blob/main/public-servers.json). <br><br>
> Also, CLaJ servers are high bandwidth consumers, as they act as relay. For an average server, around 1TB up/down of consumption per month and around 1MB/s of constant network usage.


## How to build
Pre-build releases can be found in the [releases section](https://github.com/Xpdustry/claj-v2/releases), but if you want to build the project yourself, follow the steps above.

To build the client version, simply run the command ``./gradlew client:build``. The jar file will be located in the root directory and named ``claj-v2-client.jar``.

To build the server version, simply run the command ``./gradlew server:build``. The jar file will be located in the root directory and named ``claj-v2-server.jar``.


## How it works
CLaJ is a system like [Hamachi](https://vpn.net/), that allows you to create a room and share the link to your friends. This way, they can connect to you as if they were on your private network.

The only differences are that Hamachi requires account creation and therefore the collection of personal information, etc., while CLaJ does not. And CLaJ is directly integrated into Mindustry and optimized for it, which makes it easier to use compared to Hamachi, which needs to stay in parallel of the game.

On the host player's side, their server never receives packets from people connected via CLaJ, the work is done by the CLaJ Proxy which simply executes the server's callbacks.
