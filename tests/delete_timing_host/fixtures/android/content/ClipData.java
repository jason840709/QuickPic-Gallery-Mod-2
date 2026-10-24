// SPDX-License-Identifier: MIT
package android.content;
public class ClipData {public final CharSequence label,text;private ClipData(CharSequence l,CharSequence t){label=l;text=t;}public static ClipData newPlainText(CharSequence label,CharSequence text){return new ClipData(label,text);}}
