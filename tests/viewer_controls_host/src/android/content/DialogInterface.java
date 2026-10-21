package android.content;
public interface DialogInterface {
 int BUTTON_POSITIVE=-1,BUTTON_NEGATIVE=-2;
 void dismiss();
 interface OnClickListener {void onClick(DialogInterface dialog,int which);}
 interface OnShowListener {void onShow(DialogInterface dialog);}
}
