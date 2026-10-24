// SPDX-License-Identifier: MIT
package android.content;
import android.net.Uri;import java.io.FileNotFoundException;
public class ContentResolver {public int rows,mediaCalls,documentCalls;public boolean documentResult=true;public RuntimeException failure;public Error fatal;public FileNotFoundException missing;public Uri lastUri;public String lastWhere;public String[] lastArgs;public int delete(Uri uri,String where,String[] args){mediaCalls++;lastUri=uri;lastWhere=where;lastArgs=args;if(failure!=null)throw failure;if(fatal!=null)throw fatal;return rows;}public boolean deleteDocument(Uri uri)throws FileNotFoundException {documentCalls++;lastUri=uri;if(failure!=null)throw failure;if(fatal!=null)throw fatal;if(missing!=null)throw missing;return documentResult;}}
