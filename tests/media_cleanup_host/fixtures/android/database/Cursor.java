// SPDX-License-Identifier: MIT
package android.database;public interface Cursor {boolean moveToNext();long getLong(int column);String getString(int column);int getInt(int column);void close();}
