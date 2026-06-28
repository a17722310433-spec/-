package com.wuziqi.gomoku;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private Switch switchSound, switchVibrate, switchDark, switchCoords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sp = getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        root.setBackgroundColor(Color.rgb(246, 220, 150));

        // 标题
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(62, 39, 35));
        title.setPadding(0, 8, 0, 20);
        root.addView(title);

        // 音效开关
        switchSound = addSwitch(root, "🎵 落子音效");
        switchSound.setChecked(sp.getBoolean("sound", true));
        switchSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                sp.edit().putBoolean("sound", v).apply();
            }
        });

        // 震动开关
        switchVibrate = addSwitch(root, "📳 胜利震动");
        switchVibrate.setChecked(sp.getBoolean("vibrate", true));
        switchVibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                sp.edit().putBoolean("vibrate", v).apply();
            }
        });

        // 深色棋盘
        switchDark = addSwitch(root, "🌙 深色棋盘");
        switchDark.setChecked(sp.getBoolean("dark", false));
        switchDark.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                sp.edit().putBoolean("dark", v).apply();
            }
        });

        // 坐标标注
        switchCoords = addSwitch(root, "🔤 坐标标注");
        switchCoords.setChecked(sp.getBoolean("coords", true));
        switchCoords.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean v) {
                sp.edit().putBoolean("coords", v).apply();
            }
        });

        // 间隔
        TextView space = new TextView(this);
        space.setHeight(24);
        root.addView(space);

        // 重置统计
        Button resetStats = new Button(this);
        resetStats.setText("🗑 重置胜率统计");
        resetStats.setTextSize(16);
        resetStats.setTextColor(Color.WHITE);
        resetStats.setBackgroundResource(R.drawable.bg_dialog_btn_action);
        resetStats.setPadding(24, 16, 24, 16);
        resetStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences("gomoku", Context.MODE_PRIVATE).edit()
                    .putInt("statsW", 0).putInt("statsL", 0).putInt("statsD", 0).apply();
                sp.edit().putBoolean("stats_reset", true).apply();
                finish();
            }
        });
        root.addView(resetStats);

        // 返回
        Button back = new Button(this);
        back.setText("← 返回");
        back.setTextSize(18);
        back.setTextColor(Color.WHITE);
        back.setBackgroundResource(R.drawable.bg_dialog_btn_action);
        back.setPadding(24, 16, 24, 16);
        // margin top
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 20, 0, 0);
        back.setLayoutParams(lp);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        root.addView(back);

        setContentView(root);
    }

    private Switch addSwitch(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 12, 8, 12);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(18);
        tv.setTextColor(Color.rgb(62, 39, 35));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setTextColor(Color.rgb(62, 39, 35));

        row.addView(tv);
        row.addView(sw);
        parent.addView(row);

        // divider
        View div = new View(this);
        div.setBackgroundColor(Color.rgb(160, 120, 80));
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(div);

        return sw;
    }
}