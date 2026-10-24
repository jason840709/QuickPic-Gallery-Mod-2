// SPDX-License-Identifier: MIT
package android.net;
public final class Uri {private final String value; public static int reads;private Uri(String v){value=v;} public static Uri parse(String v){return new Uri(v);}public String toString(){reads++;return value;}}
