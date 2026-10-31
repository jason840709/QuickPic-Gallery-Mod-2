// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.app.AlertDialog;import android.content.Context;import android.preference.Preference;import android.preference.PreferenceManager;import android.util.AttributeSet;import java.util.Locale;
public final class EinkPreference extends Preference{
    public EinkPreference(Context c,AttributeSet a){super(c,a);super.setPersistent(false);super.setTitle(text(c,"E-ink rendering optimization","E-ink 渲染優化","E-ink 渲染优化"));refresh();}
    private static String text(Context c,String en,String tw,String cn){Locale l=c.getResources().getConfiguration().locale;if(!"zh".equals(l.getLanguage()))return en;return "TW".equals(l.getCountry())||"HK".equals(l.getCountry())||"Hant".equals(l.getScript())?tw:cn;}
    private static String[] labels(Context c){return new String[]{text(c,"Original (default)","原始顯示（預設）","原始显示（默认）"),text(c,"Area resampling — stable tones","面積取樣：穩定網點灰階","面积取样：稳定网点灰阶"),text(c,"Lanczos3 — sharper detail","Lanczos3：保留清晰線條","Lanczos3：保留清晰线条")};}
    private void refresh(){super.setSummary(labels(super.getContext())[EinkRender.mode(super.getContext())]);}
    @Override protected void onClick(){final Context c=super.getContext();final int[] choice={EinkRender.mode(c)};
        new AlertDialog.Builder(c).setTitle(super.getTitle()).setSingleChoiceItems(labels(c),choice[0],(dialog,which)->choice[0]=which)
        .setPositiveButton(text(c,"Save","儲存","保存"),(dialog,which)->{PreferenceManager.getDefaultSharedPreferences(c).edit().putInt(EinkRender.KEY,choice[0]).apply();refresh();})
        .setNegativeButton(text(c,"Cancel","取消","取消"),null).setNeutralButton(text(c,"About","說明","说明"),(dialog,which)->new AlertDialog.Builder(c).setTitle(super.getTitle()).setMessage(text(c,"For static images. Resample for the display size instead of blurring the entire image. Bitmaps that are not being reduced receive no extra filtering. May increase loading/resampling time and reduce zoom detail. Reopen the image after changing this setting.","適用靜態圖片，依顯示尺寸重新取樣，不額外模糊整張圖片；沒有縮小的位圖不額外處理。可能增加載入與縮放後取樣時間、減少放大細節。變更後請重新開啟圖片。","适用静态图片，依显示尺寸重新取样，不额外模糊整张图片；没有缩小的位图不额外处理。可能增加载入与缩放后取样时间、减少放大细节。变更后请重新打开图片。")).setPositiveButton(text(c,"OK","確定","确定"),null).show()).show();
    }
}
