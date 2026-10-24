// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.content.ContentResolver;import android.net.Uri;import java.util.*;
public final class DeleteTiming {
 public static final List<Integer> events=new ArrayList<Integer>();public static int providerCalls;
 public static void reset(){events.clear();providerCalls=0;android.system.Os.reset();}
 public static int providerDelete(ContentResolver resolver,Uri uri,String where,String[] args){providerCalls++;return resolver.delete(uri,where,args);}
 public static void beginCleanupVerify(){events.add(10);}public static void endCleanupVerify(){events.add(11);}
 public static void beginCleanupLookup(){events.add(12);}public static void endCleanupLookup(){events.add(13);}
 public static void beginCleanupDelete(){events.add(14);}public static void endCleanupDelete(){events.add(15);}
 public static void cleanupOutcome(int code){if(code<0||code>9)throw new AssertionError("numeric outcome");events.add(code);}
}
