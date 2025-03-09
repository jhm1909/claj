package com.xpdustry.claj.client;

import arc.net.Client;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Strings;

import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;


public class CreateClajRoomDialog extends BaseDialog {
  Client client;
  CLaJ.Link link;
  
  String selected;
  Dialog add;
  Table custom, online;
  boolean valid, customShown = true, onlineShown = true;

  public CreateClajRoomDialog() {
    super("@claj.manage.name");
    
    arc.Events.run(mindustry.game.EventType.HostEvent.class, () -> {
      if (client != null) {
        client.close();
        client = null;
      }
    });
    
    cont.defaults().width(Vars.mobile ? 550f : 750f);
    
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom).disabled(b -> client != null || selected == null);
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom).disabled(b -> client == null);
    buttons.button("@claj.manage.copy", Icon.copy, this::copyLink).disabled(b -> link == null);
    
    shown(() -> {
      refreshCustom();
      refreshOnline();
    });

    // Add custom server dialog
    String[] last = {"", "", ""};
    add = new BaseDialog("@joingame.title");
    add.cont.table(table -> {
      table.add("@claj.manage.server-name").padRight(5f).right();
      table.field(last[0], text -> last[0] = text).size(320f, 54f).maxTextLength(100).left().row();
      table.add("@joingame.ip").padRight(5f).right();
      table.field(last[1], text -> last[1] = text).size(320f, 54f).valid(ip -> {
        if (ip.isEmpty()) {
          last[2] = "@claj.manage.missing-host";
          return false;
        }
        int semicolon = ip.indexOf(':');
        if (semicolon == -1 || ip.length() == semicolon+1) {
          last[2] = "@claj.manage.missing-port";
          return false;
        }
        int port = Strings.parseInt(ip.substring(semicolon+1));
        if (valid = port >= 0 && port <= 0xffff) {
          last[2] = "";
          return true;
        }
        last[2] = "@claj.manage.invalid-port";
        return false;
      }).maxTextLength(100).left().row();
      table.add();
      table.label(() -> last[2]).width(550f).left().row();
    }).row();
    add.buttons.defaults().size(140f, 60f).pad(4f);
    add.buttons.button("@cancel", add::hide);
    add.buttons.button("@ok", () -> {
      CLaJServers.custom.put(last[0], last[1]);
      CLaJServers.saveCustom();
      refreshCustom();
      add.hide();
      last[0] = last[1] = last[2] = "";
    }).disabled(b -> !valid || last[0].isEmpty() || last[1].isEmpty());

    cont.pane(hosts -> {
      //CLaJ description
      hosts.labelWrap("@claj.manage.tip").labelAlign(2, 8).padBottom(24f).width(Vars.mobile ? 550f : 750f).row();
      
      // Custom servers
      hosts.table(table -> {
        table.add("@claj.manage.custom-servers").pad(10).growX().left().color(Pal.accent);
        table.button(Icon.add, Styles.emptyi, add::show).size(40f).right().padRight(3);
        table.button(Icon.refresh, Styles.emptyi, this::refreshCustom).size(40f).right().padRight(3);
        table.button(Icon.downOpen, Styles.emptyi, () -> customShown = !customShown)
            .update(i -> i.getStyle().imageUp = !customShown ? Icon.upOpen : Icon.downOpen)
            .size(40f).right().padRight(10f);
      }).growX().row();
      hosts.image().growX().pad(5).padLeft(10).padRight(10).height(3).color(Pal.accent).row();
      hosts.collapser(table -> custom = table, true, () -> customShown).growX().padBottom(10).get().setDuration(0.1f);
      hosts.row();
      
      // Online Public servers
      hosts.table(table -> {
        table.add("@claj.manage.custom-servers").pad(10).growX().left().color(Pal.accent);
        table.button(Icon.refresh, Styles.emptyi, this::refreshOnline).size(40f).right().padRight(3);
        table.button(Icon.downOpen, Styles.emptyi, () -> onlineShown = !onlineShown)
            .update(i -> i.getStyle().imageUp = !onlineShown ? Icon.upOpen : Icon.downOpen)
            .size(40f).right().padRight(10f);
      }).growX().row();
      hosts.image().growX().pad(5).padLeft(10).padRight(10).height(3).color(Pal.accent).row();
      hosts.collapser(table -> online = table, true, () -> onlineShown).growX().padBottom(10).get().setDuration(0.1f);
      hosts.row();
    }).marginBottom(70f).get().setScrollingDisabled(true, false);

    // Adds the 'Create a CLaJ room' button
    Vars.ui.paused.shown(() -> {
      Table root = Vars.ui.paused.cont;

      if (Vars.mobile) {
        root.row().buttonRow("@claj.manage.name", Icon.planet, this::show).colspan(3)
            .disabled(button -> !Vars.net.server());//.tooltip("@claj.manage.tip");
        return;
      }

      root.row();
      root.button("@claj.manage.name", Icon.planet, this::show).colspan(2).width(450f)
          .disabled(button -> !Vars.net.server()).row();//.tooltip("@claj.manage.tip").row();

      @SuppressWarnings("rawtypes")
      arc.struct.Seq<Cell> buttons = root.getCells();
      // move the claj button above the quit button
      buttons.swap(buttons.size - 1, buttons.size - 2); 
    });
  }
  
  void refreshCustom() {
    CLaJServers.loadCustom();
    
    //TODO: ping all servers
    custom.clear();
    CLaJServers.custom.forEach(url -> 
      custom.button(url.key + " [lightgray](" + url.value + ')', Styles.cleart, () -> {
        selected = url.value;
      }).height(32f).growX().row()
    );
  }
  
  void refreshOnline() {
    CLaJServers.refreshOnline(); 
    
    //TODO: ping all servers
    online.clear();
    CLaJServers.online.forEach(url -> 
      online.button(url.key + " [lightgray](" + url.value + ')', Styles.cleart, () -> {
        selected = url.value;
      }).height(32f).growX().row()
    );
  }

  public void createRoom() {
    link = null;
    int semicolon = selected.indexOf(':');
    String ip = selected.substring(0, semicolon);
    int port = Strings.parseInt(selected.substring(semicolon + 1));
    
    try { client = CLaJ.createRoom(ip, port, link -> this.link = link, this::closeRoom); } 
    catch (Exception ignored) { Vars.ui.showErrorMessage(ignored.getMessage()); }
  }
  
  public void closeRoom() {
    client.close();
    client = null;
    link = null;
  }
  
  public void copyLink() {
    if (link == null) return;

    arc.Core.app.setClipboardText(link.toString());
    Vars.ui.showInfoFade("@copied");
  }
}
