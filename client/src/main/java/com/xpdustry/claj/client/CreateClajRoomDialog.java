package com.xpdustry.claj.client;

import arc.net.Client;
import arc.scene.ui.Dialog;
import arc.scene.ui.ScrollPane;
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
  Table local = new Table(), online = new Table();
  boolean valid, customShown, onlineShown;

  public CreateClajRoomDialog() {
    super("@claj.manage.name");
    addCloseButton();
    buttons.button("@claj.manage.create", Icon.add, this::createRoom).disabled(b -> client != null || selected == null);
    buttons.button("@claj.manage.delete", Icon.cancel, this::closeRoom).disabled(b -> client == null);
    buttons.button("@claj.manage.copy", Icon.copy, this::copyLink).disabled(b -> link == null);
    
    shown(() -> {
      refreshCustom();
      refreshOnline();
    });
    
    ScrollPane pane = new ScrollPane(cont);
    pane.setFadeScrollBars(false);
    pane.setScrollingDisabled(true, false);
    
    cont.defaults().width(Vars.mobile ? 550f : 750f);
    //cont.labelWrap("@manage.tip").labelAlign(2, 8).padBottom(16f).width(550f)
    //    .get().getStyle().fontColor = Color.lightGray;
    
    // Add custom server dialog
    String[] last = {"", ""};
    add = new BaseDialog("@joingame.title");
    add.cont.add("@claj.manage.server-name").padRight(5f).left();
    add.cont.field(last[0], text -> last[0] = text).size(320f, 54f).maxTextLength(100).row();
    add.cont.add("@joingame.ip").padRight(5f).left();
    add.cont.field(last[1], text -> last[1] = text).size(320f, 54f).valid(this::isValidIP).maxTextLength(100).row();
    add.buttons.defaults().size(140f, 60f).pad(4f);
    add.buttons.button("@cancel", add::hide);
    add.buttons.button("@ok", () -> {
      CLaJServers.custom.put(last[0], last[1]);
      CLaJServers.saveCustom();
      refreshCustom();
      add.hide();
    }).disabled(b -> !valid || last[0].isEmpty() || last[1].isEmpty());
    
    // Custom servers
    cont.table(name -> {
      name.add("@claj.manage.custom-servers").pad(10).growX().left().color(Pal.accent);
      name.button(Icon.add, Styles.emptyi, add::show).size(40f).right().padRight(3);
      name.button(Icon.refresh, Styles.emptyi, this::refreshCustom).size(40f).right().padRight(3);
      name.button(Icon.downOpen, Styles.emptyi, () -> customShown = !customShown)
          .update(i -> i.getStyle().imageUp = !customShown ? Icon.upOpen : Icon.downOpen)
          .size(40f).right().padRight(10f);
    }).row();
    cont.image().growX().pad(5).padLeft(10).padRight(10).height(3).color(Pal.accent).row();
    cont.collapser(table -> {
      local = table;
      CLaJServers.custom.forEach(url -> {
        local.button(url.key + " [lightgray](" + url.value + ')', Styles.cleart, () -> {
          selected = url.value;
        }).height(32f).growX().row();
      });
      //TODO
    }, true, () -> customShown).row();
    
    // Online Public servers
    cont.table(name -> {
      name.add("@claj.manage.custom-servers").pad(10).growX().left().color(Pal.accent);
      name.button(Icon.refresh, Styles.emptyi, this::refreshOnline).size(40f).right().padRight(3);
      name.button(Icon.downOpen, Styles.emptyi, () -> customShown = !customShown)
          .update(i -> i.getStyle().imageUp = !customShown ? Icon.upOpen : Icon.downOpen)
          .size(40f).right().padRight(10f);
    }).row();
    cont.image().growX().pad(5).padLeft(10).padRight(10).height(3).color(Pal.accent).row();
    cont.collapser(table -> {
      online = table;
      //TODO
      CLaJServers.online.forEach(url -> {
        online.button(url.key + " [lightgray](" + url.value + ')', Styles.cleart, () -> {
          selected = url.value;
        }).height(32f).growX().row();
      });
    }, true, () -> customShown).row();
    
    
    
    // Adds the 'Create a CLaJ room' button
    Vars.ui.paused.shown(() -> {
      Table root = Vars.ui.paused.cont;

      if (Vars.mobile) {
        root.row().buttonRow("@claj.manage.name", Icon.planet, this::show).colspan(3)
            .disabled(button -> !Vars.net.server()).tooltip("@claj.manage.tip");
        return;
      }

      root.row();
      root.button("@claj.manage.name", Icon.planet, this::show).colspan(2).width(450f)
          .disabled(button -> !Vars.net.server()).tooltip("@claj.manage.tip").row();

      @SuppressWarnings("rawtypes")
      arc.struct.Seq<Cell> buttons = root.getCells();
      // move the claj button above the quit button
      buttons.swap(buttons.size - 1, buttons.size - 2); 
    });
  }
  
  boolean isValidIP(String url) {
    if (!url.contains(":")) return false;
    int port = Strings.parseInt(url.substring(url.indexOf(':') + 1));
    return valid = port >= 0 && port <= 0xffff;
  }
  
  void refreshCustom() {
    CLaJServers.loadCustom();
    //TODO: ping all servers
  }
  
  void refreshOnline() {
    CLaJServers.refreshOnline(); 
    //TODO: ping all servers
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
    link = null;
    selected = null;
  }
  
  public void copyLink() {
    if (link == null) return;

    arc.Core.app.setClipboardText(link.toString());
    Vars.ui.showInfoFade("@copied");
  }
}
