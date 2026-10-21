package com.alensw.b.g;
import android.view.*;
import java.util.*;
/** Boundary shape of the old App Menu, including its actual obfuscated name. */
public abstract class b implements Menu {
 private final ArrayList<MenuItem> e=new ArrayList<MenuItem>();
 public int adds,removes;
 protected b(int... ids){for(int id:ids)e.add(newItem(id));}
 protected abstract MenuItem newItem(int id);
 protected List<MenuItem> hostItems(){return e;}
 protected Object hostStorage(){return e;}
 public MenuItem findItem(int id){for(MenuItem item:e)if(item.getItemId()==id)return item;return null;}
 public void removeItem(int id){MenuItem item=findItem(id);if(item!=null){e.remove(item);removes++;}}
 public MenuItem add(int group,int id,int order,int title){MenuItem item=newItem(id);item.setTitle(title);e.add(item);adds++;return item;}
 public MenuItem add(int group,int id,int order,CharSequence title){MenuItem item=newItem(id);item.setTitle(title);e.add(item);adds++;return item;}
 public int size(){return e.size();}
 public MenuItem getItem(int index){return e.get(index);}
}
