# Copy Link and Join v2 (CLaJ v2)
This system allow you to play with your friends just by creating a room, copy the link, send it to your friends.

This is a bundled, reworked and optimized version of the [CLaJ server](https://github.com/xzxADIxzx/Copy-Link-and-Join) and the [xzxADIxzx's Scheme-Size mod](https://github.com/xzxADIxzx/Scheme-Size), with only the CLaJ feature.

> [!IMPORTANT]
> The CLaJ version 2 is not compatible with the older one. <br>
> The protocol has been reworked and optimized, and CLaJ links have also been changed to a better version.

## How to use
### Client
Start and host a map as normal (or your campaign): ``Host Multiplayer Game`` **>** ``Host``. <br>
Then go to ``Create a CLaJ Room``, select a server (or add your own), after ``Create Room`` **>** ``Copy Link`` and send the copied link to your friends.

To join, it's simple, copy the link your friend sent you, open your game, go to ``Play`` **>** ``Join Game`` **>** ``Join via CLaJ``, paste the link and ``OK``. <br>
Now, if all goods, you can play with your friends, so enjoy =).


### Server
> [!NOTE]
> The server version is not a plugin, it's a custom server.

To host a server, just run the command ``java -jar claj-server.jar <port>``, where ``<port>`` is the port for the server. <br>
Also don't forget to open the port in TCP and UDP mode on your end-point and redirect it to the host machine.

A CLaJ server doesn't need much memory and cpu, 256MB of memory and one core are enough, even at high traffic.<br>
To change the memory allocated to the server, change the command to ``java -Xms<memory> -Xmx<memory> -jar claj-server.jar <port>``, where ``<memory>`` is the memory allocated to the server *(e.g. 256MB)*.

> [!IMPORTANT]
> Please note that, if you plan to make the server public, you can create a Pull-Request to add it to the public server list, in [public-servers.json](https://github.com/xpdustry/claj-v2/blob/main/public-servers.json). <br><br>
> Also, CLaJ servers are high bandwidth consumers, as they act as proxies. For an average server, around 1TB up/down of consumption per month and around 1MB/s of constant network usage.


## How to build
Pre-build releases can be found in the [releases section](https://github.com/Xpdustry/claj-v2/releases), but if you want to build the project yourself, follow the steps above.

To build the client version, simply run the command ``./gradlew client:build``. The jar file will be located in the root directory and named ``claj-client.jar``.

To build the server version, simply run the command ``./gradlew server:build``. The jar file will be located in the root directory and named ``claj-server.jar``.


## Notes
* make a system to remove the claj server from the dosBlacklist, because if a client send too many packets, the host will sets the claj server address to the list, because for him, it's only n connections from the same address (the claj server). And same for clients.
* The current server implementation doesn't know where to send packets, so the host need to keep alive a connection for each client and send n times the same packet for each connection, like that the server use the connection id to know where send the packets. <br>
So need to make a packet wrapper that add the target connection using the id, given by the server when a new player join the room. <br>
Note: need to see the steam implementation for inspiration. 
