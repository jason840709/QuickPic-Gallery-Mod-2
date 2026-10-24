// SPDX-License-Identifier: MIT
package android.content;import android.net.Uri;public final class ContentUris {public static Uri withAppendedId(Uri uri,long id){return Uri.parse(uri.toString()+"/"+id);}}
