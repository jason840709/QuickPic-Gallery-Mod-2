// SPDX-License-Identifier: MIT
package android.provider;
import android.content.ContentResolver;import android.net.Uri;import java.io.FileNotFoundException;
public class DocumentsContract {public static boolean deleteDocument(ContentResolver resolver,Uri uri)throws FileNotFoundException {return resolver.deleteDocument(uri);}}
