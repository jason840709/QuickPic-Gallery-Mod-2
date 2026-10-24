// SPDX-License-Identifier: MIT
package android.net;
import java.net.URI;import java.net.URLDecoder;import java.util.*;
public final class Uri {
 private final String text;private final URI value;
 private Uri(String s){text=s;value=URI.create(s);}public static Uri parse(String s){return new Uri(s);}
 public String getScheme(){return value.getScheme();}public String getAuthority(){return value.getRawAuthority();}
 public String getQuery(){return value.getRawQuery();}public String getFragment(){return value.getRawFragment();}
 public List<String> getPathSegments(){List<String> out=new ArrayList<String>();String p=value.getRawPath();if(p!=null)for(String s:p.split("/"))if(!s.isEmpty())try{out.add(URLDecoder.decode(s,"UTF-8"));}catch(Exception e){throw new IllegalArgumentException(e);}return out;}
 public String toString(){return text;}
}
