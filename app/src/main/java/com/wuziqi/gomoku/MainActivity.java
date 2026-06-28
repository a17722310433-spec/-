package com.wuziqi.gomoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private GomokuView gomokuView;
    private TextView statusText, statsText;
    private Button colorToggleButton, pvpButton, levelButton;
    private View replayBar;
    private Button replayPrev, replayNext, replayExit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        statusText = (TextView) findViewById(R.id.status_text);
        statsText = (TextView) findViewById(R.id.stats_text);
        gomokuView = (GomokuView) findViewById(R.id.gomoku_view);
        final Button undoButton = (Button) findViewById(R.id.undo_button);
        final Button restartButton = (Button) findViewById(R.id.restart_button);
        final Button hintButton = (Button) findViewById(R.id.hint_button);
        colorToggleButton = (Button) findViewById(R.id.color_toggle_button);
        pvpButton = (Button) findViewById(R.id.pvp_button);
        levelButton = (Button) findViewById(R.id.level_button);
        final Button watchButton = (Button) findViewById(R.id.watch_button);
        final Button settingsButton = (Button) findViewById(R.id.settings_button);
        final Button networkButton = (Button) findViewById(R.id.network_button);

        replayBar = findViewById(R.id.replay_bar);
        replayPrev = (Button) findViewById(R.id.replay_prev_button);
        replayNext = (Button) findViewById(R.id.replay_next_button);
        replayExit = (Button) findViewById(R.id.replay_exit_button);

        gomokuView.setGameListener(new GomokuView.GameListener() {
            @Override
            public void onStatusChanged(String status) {
                statusText.setText(status);
                updateStats();
            }
            @Override
            public void onGameOver(String title, String message) {
                showGameOverDialog(title, message);
            }
        });

        // 颜色切换
        colorToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockDuringWatchOrReplay()) return;
                gomokuView.setHumanBlack(!gomokuView.isHumanBlack());
                updateColorButton();
            }
        });

        // 双人模式
        pvpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockDuringWatchOrReplay()) return;
                boolean now = !gomokuView.isPvpMode();
                gomokuView.setPvpMode(now);
                updatePvpButton(now);
                updateStats();
            }
        });

        // 难度循环：简单→普通→困难→大师→简单...
        levelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockDuringWatchOrReplay()) return;
                int next = gomokuView.getAiLevel() % 4 + 1;
                gomokuView.setAiLevel(next);
                gomokuView.resetGame();
                updateLevelButton();
            }
        });

        // 操作按钮
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (blockDuringWatchOrReplay()) return;
                gomokuView.stopWatchMode();
                gomokuView.resetGame();
            }
        });
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { if (blockDuringWatchOrReplay()) return; gomokuView.undoLastTurn(); }
        });
        hintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { if (blockDuringWatchOrReplay()) return; gomokuView.showHint(); }
        });

        // 观战
        watchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (gomokuView.isWatchMode()) {
                    gomokuView.stopWatchMode();
                    gomokuView.resetGame();
                    watchButton.setText("观战");
                } else {
                    gomokuView.startWatchMode();
                    watchButton.setText("停止");
                }
            }
        });

        // 设置/联机
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        networkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, NetworkActivity.class)); }
        });

        // 回放
        replayPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { gomokuView.replayPrev(); }
        });
        replayNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { gomokuView.replayNext(); }
        });
        replayExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gomokuView.exitReplayMode();
                replayBar.setVisibility(View.GONE);
                gomokuView.resetGame();
            }
        });

        updateColorButton();
        updatePvpButton(gomokuView.isPvpMode());
        updateLevelButton();
        updateStats();
    }

    @Override
    public void onBackPressed() {
        if (gomokuView.isReplayMode()) {
            gomokuView.exitReplayMode();
            replayBar.setVisibility(View.GONE);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("确认退出")
            .setMessage("确定要退出吗？当前对局已自动保存。")
            .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int i) { finish(); }
            })
            .setNegativeButton("继续下", null)
            .show();
    }

    @Override protected void onPause() { super.onPause(); gomokuView.saveState(); }

    @Override
    protected void onResume() {
        super.onResume();
        gomokuView.reloadSettings();
        if (getSharedPreferences("gomoku_settings", MODE_PRIVATE).getBoolean("stats_reset", false)) {
            getSharedPreferences("gomoku_settings", MODE_PRIVATE).edit().putBoolean("stats_reset", false).apply();
        }
        if (gomokuView.restoreState()) {
            updateColorButton();
            updatePvpButton(gomokuView.isPvpMode());
            updateLevelButton();
            updateStats();
        }
    }



    private void updateColorButton() {
        boolean black = gomokuView.isHumanBlack();
        colorToggleButton.setText(black ? "● 黑棋" : "○ 白棋");
        colorToggleButton.setTextColor(Color.WHITE);
        colorToggleButton.setBackgroundResource(R.drawable.bg_button_selected);
    }

    private void updatePvpButton(boolean pvp) {
        pvpButton.setText(pvp ? "✓ 双人" : "双人");
        pvpButton.setTextColor(pvp ? Color.WHITE : Color.rgb(74, 44, 26));
        pvpButton.setBackgroundResource(pvp ? R.drawable.bg_button_selected : R.drawable.bg_button_normal);
        // PvP 模式下隐藏难度按钮
        levelButton.setVisibility(pvp ? View.GONE : View.VISIBLE);
    }

    private void updateLevelButton() {
        int level = gomokuView.getAiLevel();
        String[] names = {"", "简单", "普通", "困难", "大师"};
        String name = level >= 1 && level <= 4 ? names[level] : "普通";
        levelButton.setText("🤖 " + name);
        // 默认难度(普通)和最高难度(大师)高亮
        boolean highlight = level == 2 || level == 4;
        levelButton.setTextColor(highlight ? Color.WHITE : Color.rgb(74, 44, 26));
        levelButton.setBackgroundResource(highlight ? R.drawable.bg_button_selected : R.drawable.bg_button_normal);
    }

    private void updateStats() {
        statsText.setText("胜 " + gomokuView.getStatsWins() + "  负 " + gomokuView.getStatsLosses() + "  平 " + gomokuView.getStatsDraws());
    }

    private void showGameOverDialog(String title, String message) {
        LinearLayout layout = DialogHelper.createLayout(this, 36, 24, 36, 20);
        layout.addView(DialogHelper.createTitle(this, title, 22));
        layout.addView(DialogHelper.createDivider(this));
        layout.addView(DialogHelper.createMessage(this, message + "\n\n再来一局？", 16));

        Button btnRestart = DialogHelper.createActionButton(this, "再来一局");
        Button btnReplay = DialogHelper.createNormalButton(this, "📽 回放");
        Button btnView = DialogHelper.createNormalButton(this, "看看棋盘");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 56, 1f);
        lp.setMargins(3, 0, 3, 0);
        btnRestart.setLayoutParams(lp);
        row.addView(btnRestart);
        btnReplay.setLayoutParams(lp);
        row.addView(btnReplay);
        btnView.setLayoutParams(lp);
        row.addView(btnView);
        layout.addView(row);

        final AlertDialog dialog = DialogHelper.showDialog(this, layout, 0.90f);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); replayBar.setVisibility(View.GONE); gomokuView.resetGame(); }
        });
        btnReplay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); replayBar.setVisibility(View.VISIBLE); gomokuView.enterReplayMode(); }
        });
        btnView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
    }

    private boolean blockDuringWatchOrReplay() {
        if (gomokuView.isWatchMode()) {
            Toast.makeText(this, "请先退出观战", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (gomokuView.isReplayMode()) {
            Toast.makeText(this, "请先退出回放", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }
}
