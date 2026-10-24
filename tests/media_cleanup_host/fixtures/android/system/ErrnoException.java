// SPDX-License-Identifier: MIT
package android.system;public class ErrnoException extends Exception {private static final long serialVersionUID=1L;public final int errno;public ErrnoException(String f,int e){super(f);errno=e;}}
