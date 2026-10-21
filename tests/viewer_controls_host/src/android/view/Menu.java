package android.view;
public interface Menu {
 int NONE=0;
 MenuItem findItem(int id);
 void removeItem(int id);
 MenuItem add(int group,int id,int order,int title);
 MenuItem add(int group,int id,int order,CharSequence title);
 int size();
 MenuItem getItem(int index);
}
