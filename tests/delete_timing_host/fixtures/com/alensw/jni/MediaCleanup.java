// SPDX-License-Identifier: MIT
package com.alensw.jni;
/** Dispatch-only stub: does not prove absence, query a provider or implement cleanup policy. */
public final class MediaCleanup {public static int calls;public static int delete(android.content.ContentResolver r,android.net.Uri u,String w,String[] a){calls++;return DeleteTiming.providerDelete(r,u,w,a);}}
