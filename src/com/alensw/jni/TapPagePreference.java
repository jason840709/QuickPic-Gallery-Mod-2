// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Six portrait diagrams with opt-in navigation and a shared direction switch. */
public final class TapPagePreference extends Preference {
    private static final String KEY = "tap_page_layout", REVERSE = "tap_page_reverse";
    private static final int[] MODES = {4, 5, 6, 1, 2, 7};
    private static final String[][] LABELS = {
        {"Disabled", "Bottom corners", "Both edges", "", "Left edge", "Right edge", "Middle edges", "L-shaped"},
        {"停用", "下方兩角", "左右兩側", "", "左側", "右側", "中間兩側", "L 型"},
        {"停用", "下方两角", "左右两侧", "", "左侧", "右侧", "中间两侧", "L 型"}
    };
    public TapPagePreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        super.setPersistent(false);
        super.setTitle(text(context, "Tap to turn pages", "點擊翻頁", "点击翻页"));
        refreshSummary();
    }
    public static int mode(Context context) {
        if (context == null) return 0;
        try {
            int mode = PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY, 0);
            // Retired top/bottom layout is disabled rather than silently becoming L-shaped.
            return mode >= 0 && mode < TapPageLayout.COUNT && mode != 3 ? mode : 0;
        } catch (RuntimeException ignored) { return 0; }
    }
    public static boolean reversed(Context context) {
        if (context == null) return false;
        try { return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(REVERSE, false); }
        catch (RuntimeException ignored) { return false; }
    }
    private static int language(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        if (locale == null || !"zh".equals(locale.getLanguage())) return 0;
        return "TW".equals(locale.getCountry()) || "HK".equals(locale.getCountry())
                || "MO".equals(locale.getCountry()) || "Hant".equals(locale.getScript()) ? 1 : 2;
    }
    private static String text(Context c, String en, String tw, String cn) {
        int l = language(c); return l == 1 ? tw : l == 2 ? cn : en;
    }
    private void refreshSummary() {
        Context c = super.getContext(); int mode = mode(c);
        super.setSummary(LABELS[language(c)][mode] + (mode != 0 && reversed(c)
                ? text(c, " · Reversed", "・已反轉", "・已反转") : ""));
    }
    @Override protected void onClick() {
        final Context c = super.getContext();
        final int[] chosen = {mode(c) == 0 ? TapPageLayout.BOTTOM_CORNERS : mode(c)};
        final boolean[] reverse = {reversed(c)};
        final Diagram[] diagrams = new Diagram[MODES.length];
        LinearLayout title = new LinearLayout(c);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(c,16), dp(c,10), dp(c,12), dp(c,4));
        TextView heading = new TextView(c);
        heading.setText(text(c,"Tap areas","點擊翻頁","点击翻页")); heading.setTextSize(20);
        title.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Switch flip = new Switch(c);
        flip.setText(text(c,"Reverse","翻頁反轉","翻页反转")); flip.setTextSize(14);
        flip.setSwitchPadding(dp(c,6)); flip.setMinHeight(dp(c,48)); flip.setChecked(reverse[0]);
        title.addView(flip);
        LinearLayout content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c,8), dp(c,4), dp(c,8),0);
        LinearLayout grid = new LinearLayout(c); grid.setOrientation(LinearLayout.VERTICAL);
        for (int row=0;row<2;row++) {
            LinearLayout line = new LinearLayout(c);
            for (int col=0;col<3;col++) {
                final int index = row*3+col;
                Diagram diagram = new Diagram(c, MODES[index], chosen[0] == MODES[index], reverse[0]);
                diagrams[index] = diagram;
                ((View) diagram).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        chosen[0] = MODES[index];
                        for (Diagram d : diagrams) d.update(d.mode == chosen[0], reverse[0]);
                    }
                });
                line.addView(diagram, new LinearLayout.LayoutParams(0, dp(c,154), 1));
            }
            grid.addView(line);
        }
        flip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                reverse[0] = checked;
                for (Diagram d : diagrams) d.update(d.mode == chosen[0], checked);
            }
        });
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(false); scroll.addView(grid);
        int available = Math.max(dp(c,100), Math.min(dp(c,308),
                c.getResources().getDisplayMetrics().heightPixels - dp(c,240)));
        content.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,available));
        TextView legend = new TextView(c); legend.setGravity(Gravity.CENTER);
        legend.setTextSize(13); legend.setPadding(0,dp(c,8),0,dp(c,8));
        legend.setText(text(c,"← Previous     ☰ Menu     Next →","← 上一頁     ☰ 選單     下一頁 →","← 上一页     ☰ 菜单     下一页 →"));
        content.addView(legend);
        final Switch enabled = new Switch(c);
        enabled.setText(text(c,"Enable tap navigation","啟用點擊翻頁","启用点击翻页"));
        enabled.setTextSize(16); enabled.setChecked(mode(c) != 0); enabled.setMinHeight(dp(c,48));
        enabled.setPadding(dp(c,8),0,dp(c,8),0);
        content.addView(enabled, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(c).setCustomTitle(title).setView(content)
            .setNegativeButton(text(c,"Cancel","取消","取消"),null)
            .setPositiveButton(text(c,"Save","儲存","保存"),new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog,int which) {
                    try {
                        PreferenceManager.getDefaultSharedPreferences(c).edit()
                            .putInt(KEY,enabled.isChecked()?chosen[0]:0).putBoolean(REVERSE,reverse[0]).apply();
                        refreshSummary();
                    } catch (RuntimeException error) {
                        Toast.makeText(c,text(c,"Unable to save setting","無法儲存設定","无法保存设置"),Toast.LENGTH_SHORT).show();
                    }
                }
            }).show();
    }
    private static int dp(Context c,int value) { return Math.round(c.getResources().getDisplayMetrics().density*value); }
    /** Screen outline is always portrait 1860:2480 (3:4), including landscape settings. */
    private static final class Diagram extends View {
        final int mode;
        private boolean selected, reverse;
        private final int labelColor, accentColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Diagram(Context c,int mode,boolean selected,boolean reverse) {
            super(c); this.mode=mode;
            android.content.res.TypedArray colors=c.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
            labelColor=colors.getColor(0,Color.BLACK);colors.recycle();
            int brightness=(Color.red(labelColor)+Color.green(labelColor)+Color.blue(labelColor))/3;
            accentColor=brightness>128?0xff90caf9:0xff1565c0;
            super.setFocusable(true); update(selected,reverse);
        }
        void update(boolean selected,boolean reverse) {
            this.selected=selected;this.reverse=reverse;super.setSelected(selected);
            super.setContentDescription(LABELS[language(super.getContext())][mode]
                    + (reverse?text(super.getContext(),", reversed","，已反轉","，已反转"):""));
            super.invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); Context c=super.getContext();
            float width=Math.min(super.getWidth()-dp(c,24),dp(c,87));
            if(width<=0)return;
            float height=width*4/3, left=(super.getWidth()-width)/2, top=dp(c,8);
            RectF screen=new RectF(left,top,left+width,top+height);
            // Fill row runs using the same hit geometry as the image viewer.
            paint.setStyle(Paint.Style.FILL);paint.setAntiAlias(false);
            final int columns=60,rows=80;
            for(int y=0;y<rows;y++) {
                int start=0, action=TapPageLayout.action(mode,reverse,.5f,y+.5f,columns,rows);
                for(int x=1;x<=columns;x++) {
                    int next=x==columns?2:TapPageLayout.action(mode,reverse,x+.5f,y+.5f,columns,rows);
                    if(next!=action) {
                        paint.setColor(action<0?0xfff4c8c3:action>0?0xffb9d7ef:0xffffedc8);
                        canvas.drawRect(left+start*width/columns,top+y*height/rows,
                                left+x*width/columns,top+(y+1)*height/rows,paint);
                        start=x;action=next;
                    }
                }
            }
            paint.setAntiAlias(true);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(c,1));paint.setColor(0xff333333);
            canvas.drawRoundRect(screen,dp(c,3),dp(c,3),paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(c,21));
            float[] positions;
            switch(mode) {
                case 1:positions=new float[]{.16f,.75f,.50f,.26f,.84f,.75f};break;
                case 2:positions=new float[]{.15f,.50f,.50f,.50f,.85f,.50f};break;
                case 4:positions=new float[]{.15f,.25f,.63f,.50f,.15f,.75f};break;
                case 5:positions=new float[]{.85f,.25f,.37f,.50f,.85f,.75f};break;
                case 6:positions=new float[]{.15f,.50f,.50f,.50f,.85f,.50f};break;
                default:positions=new float[]{.17f,.20f,.50f,.50f,.83f,.80f};break;
            }
            label(canvas,reverse?"→":"←",left+width*positions[0],top+height*positions[1]);
            label(canvas,"☰",left+width*positions[2],top+height*positions[3]);
            label(canvas,reverse?"←":"→",left+width*positions[4],top+height*positions[5]);
            if(selected) {
                paint.setStyle(Paint.Style.STROKE);paint.setColor(accentColor);paint.setStrokeWidth(dp(c,3));
                canvas.drawRoundRect(new RectF(left-dp(c,4),top-dp(c,4),left+width+dp(c,4),top+height+dp(c,4)),dp(c,6),dp(c,6),paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(selected?accentColor:labelColor);
            paint.setFakeBoldText(selected);paint.setTextSize(dp(c,14));
            label(canvas,LABELS[language(c)][mode],super.getWidth()/2f,top+height+dp(c,18));
            paint.setFakeBoldText(false);
        }
        private void label(Canvas canvas,String text,float x,float y) {
            canvas.drawText(text,x,y-(paint.ascent()+paint.descent())/2,paint);
        }
    }
}
