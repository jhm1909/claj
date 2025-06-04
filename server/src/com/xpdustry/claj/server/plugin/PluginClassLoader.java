package com.xpdustry.claj.server.plugin;

import java.net.URL;
import java.net.URLClassLoader;

import arc.files.Fi;
import arc.struct.Seq;


public class PluginClassLoader extends ClassLoader {
  private Seq<ClassLoader> children = new Seq<>();
  private boolean inChild = false;

  public PluginClassLoader(ClassLoader parent) {
    super(parent);
  }
  
  public void loadAndAdd(Fi jar) throws Exception {
    addChild(JarLoader.load(jar, this));
  }
  
  public void addChild(ClassLoader child) {
    children.add(child);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    //a child may try to delegate class loading to its parent, which is *this class loader* - do not let that happen
    if (inChild) {
      inChild = false;
      throw new ClassNotFoundException(name);
    }

    ClassNotFoundException last = null;
    int size = children.size;

    //if it doesn't exist in the main class loader, try all the children
    for (int i=0; i<size; i++) {
      try {
        try {
          inChild = true;
          return children.get(i).loadClass(name);
        } finally {
          inChild = false;
        }
      } catch (ClassNotFoundException e) {
          last = e;
      }
    }

    throw (last == null ? new ClassNotFoundException(name) : last);
  }
  
  
  public static class JarLoader {
    public static ClassLoader load(Fi jar, ClassLoader parent) throws Exception {
      return new URLClassLoader(new URL[] {jar.file().toURI().toURL()}, parent){
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
          //check for loaded state
          Class<?> loadedClass = findLoadedClass(name);
          if (loadedClass == null) {
            //try to load own class first
            try { loadedClass = findClass(name); }
            //use parent if not found
            catch (ClassNotFoundException e) { return parent.loadClass(name); }
          }

          if (resolve) resolveClass(loadedClass);
          return loadedClass;
        }
      };
    }
  }

}
