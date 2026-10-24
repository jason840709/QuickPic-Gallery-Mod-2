// SPDX-License-Identifier: MIT
package android.content;
public class ClipboardManager {public ClipData clip;public int writes;public void setPrimaryClip(ClipData c){writes++;clip=c;}}
