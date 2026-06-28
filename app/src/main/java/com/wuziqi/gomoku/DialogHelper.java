package com.wuziqi.gomoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 通用五子棋对话框工具 — 统一弹窗风格，减少重复代码
 */
public class DialogHelper {

    // ===== 主题色常量 =====
    public static final int BROWN_DARK   = Color.rgb(62, 39, 35);   // 深棕（标题/文字）
    public static final int BROWN_MID    = Color.rgb(74, 44, 26);   // 中棕（普通按钮文字）
    public static final int BROWN_LIGHT  = Color.rgb(100, 60, 30);  // 浅棕（状态文字）
    public static final int BG_BOARD     = Color.rgb(246, 220, 150); // 棋盘底色
    public static final int GOLD_LINE    = Color.rgb(181, 138, 72);  // 分隔线金色

    /**
     * 创建通用棋盘风格对话框
     * @param activity  上下文
     * @param content   对话框内容View
     * @param widthPercent 宽度占屏幕百分比 (0~1)
     * @return 对话框
     */
    public static AlertDialog showDialog(Activity activity, View content, float widthPercent) {
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.GomokuDialog)
                .setView(content).create();
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            int width = (int)(activity.getResources().getDisplayMetrics().widthPixels * widthPercent);
            win.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    /**
     * 创建纵向布局容器（棋盘风格背景+内边距）
     */
    public static LinearLayout createLayout(Activity activity, int padL, int padT, int padR, int padB) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.bg_dialog);
        layout.setPadding(padL, padT, padR, padB);
        return layout;
    }

    /**
     * 创建弹窗标题
     */
    public static TextView createTitle(Activity activity, String text, float textSize) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(textSize);
        tv.setTextColor(BROWN_DARK);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 4, 0, 8);
        return tv;
    }

    /**
     * 创建弹窗消息文字
     */
    public static TextView createMessage(Activity activity, String text, float textSize) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(textSize);
        tv.setTextColor(BROWN_MID);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, 20);
        return tv;
    }

    /**
     * 创建分隔线
     */
    public static View createDivider(Activity activity) {
        View div = new View(activity);
        div.setBackgroundColor(GOLD_LINE);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return div;
    }

    /**
     * 创建操作按钮（深色高亮风格）
     */
    public static Button createActionButton(Activity activity, String text) {
        return createButton(activity, text, Color.WHITE, R.drawable.bg_dialog_btn_action);
    }

    /**
     * 创建普通按钮（浅色风格）
     */
    public static Button createNormalButton(Activity activity, String text) {
        return createButton(activity, text, BROWN_MID, R.drawable.bg_dialog_btn_normal);
    }

    private static Button createButton(Activity activity, String text, int textColor, int bgRes) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextSize(16);
        btn.setTextColor(textColor);
        btn.setBackgroundResource(bgRes);
        btn.setPadding(16, 0, 16, 0);
        return btn;
    }

    /**
     * 创建并排行按钮（两个按钮等宽）
     * @param btn1      左按钮
     * @param btn2      右按钮
     * @param btnHeight 按钮高度(dp)
     */
    public static LinearLayout createButtonRow(Activity activity, Button btn1, Button btn2, int btnHeight) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, btnHeight, 1f);
        lp1.setMargins(0, 0, 6, 0);
        btn1.setLayoutParams(lp1);
        row.addView(btn1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, btnHeight, 1f);
        lp2.setMargins(6, 0, 0, 0);
        btn2.setLayoutParams(lp2);
        row.addView(btn2);
        return row;
    }
}
