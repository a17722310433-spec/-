package com.wuziqi.gomoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkActivity extends Activity implements NetworkGame.NetworkListener {
    private NetworkGame networkGame;
    private TextView statusText, ipText;
    private EditText ipInput;
    private Button createBtn, connectBtn, disconnectBtn;
    private GomokuView gomokuView;
    private boolean isBlack;
    private SharedPreferences sp;
    private AlertDialog gameOverDialog;
    private AlertDialog scanDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("gomoku_network", Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        root.setBackgroundColor(Color.rgb(246, 220, 150));

        // 标题
        TextView title = new TextView(this);
        title.setText("局域网对战");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(62, 39, 35));
        title.setPadding(0, 8, 0, 16);
        root.addView(title);

        // 本机IP + 状态合并到一行
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        ipText = new TextView(this);
        ipText.setText("本机IP: " + NetworkGame.getLocalIpAddress());
        ipText.setTextSize(14);
        ipText.setTextColor(Color.rgb(100, 60, 30));
        ipText.setPadding(0, 4, 0, 8);
        infoRow.addView(ipText);
        statusText = new TextView(this);
        statusText.setText("选择创建或加入房间");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.rgb(100, 60, 30));
        statusText.setPadding(0, 4, 0, 8);
        statusText.setGravity(Gravity.RIGHT);
        statusText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        infoRow.addView(statusText);
        root.addView(infoRow);

        // IP输入行：输入框 + 扫描按钮（同一行）
        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setOrientation(LinearLayout.HORIZONTAL);
        ipRow.setPadding(0, 4, 0, 0);
        ipInput = new EditText(this);
        ipInput.setHint("对方IP地址");
        ipInput.setTextSize(16);
        ipInput.setTextColor(Color.rgb(62, 39, 35));
        ipInput.setBackgroundResource(R.drawable.bg_dialog_btn_normal);
        ipInput.setPadding(12, 0, 12, 0);
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(0, 52, 1f);
        ipInput.setLayoutParams(ipLp);
        String savedIp = sp.getString("last_ip", "");
        if (!savedIp.isEmpty()) ipInput.setText(savedIp);
        ipRow.addView(ipInput);
        final Button scanBtn = new Button(this);
        scanBtn.setText("🔍 扫描");
        scanBtn.setTextSize(16);
        scanBtn.setTextColor(Color.rgb(74, 44, 26));
        scanBtn.setBackgroundResource(R.drawable.bg_button_normal);
        scanBtn.setPadding(12, 0, 12, 0);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(0, 52, 0.4f);
        scanLp.setMargins(8, 0, 0, 0);
        scanBtn.setLayoutParams(scanLp);
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBtn.setEnabled(false);
                scanBtn.setText("⏳ 扫描");
                statusText.setText("正在扫描局域网...");
                scanLans(scanBtn, uiHandler);
            }
        });
        ipRow.addView(scanBtn);
        root.addView(ipRow);

        // 操作按钮行：创建房间 + 加入房间（同一行）
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setPadding(0, 10, 0, 0);
        createBtn = new Button(this);
        createBtn.setText("✨ 创建房间");
        createBtn.setTextSize(16);
        createBtn.setTextColor(Color.WHITE);
        createBtn.setBackgroundResource(R.drawable.bg_dialog_btn_action);
        createBtn.setPadding(12, 0, 12, 0);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, 56, 1f);
        lp1.setMargins(0, 0, 8, 0);
        createBtn.setLayoutParams(lp1);
        createBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createRoom();
            }
        });
        btnLayout.addView(createBtn);
        connectBtn = new Button(this);
        connectBtn.setText("🔗 加入房间");
        connectBtn.setTextSize(16);
        connectBtn.setTextColor(Color.rgb(74, 44, 26));
        connectBtn.setBackgroundResource(R.drawable.bg_dialog_btn_normal);
        connectBtn.setPadding(12, 0, 12, 0);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, 56, 1f);
        lp2.setMargins(8, 0, 0, 0);
        connectBtn.setLayoutParams(lp2);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = ipInput.getText().toString().trim();
                if (ip.isEmpty()) {
                    Toast.makeText(NetworkActivity.this, "请输入对方IP地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                sp.edit().putString("last_ip", ip).apply();
                connectToServer(ip);
            }
        });
        btnLayout.addView(connectBtn);
        root.addView(btnLayout);

        // 断开连接按钮
        disconnectBtn = new Button(this);
        disconnectBtn.setText("断开连接");
        disconnectBtn.setTextSize(16);
        disconnectBtn.setTextColor(Color.WHITE);
        disconnectBtn.setBackgroundResource(R.drawable.bg_dialog_btn_action);
        disconnectBtn.setPadding(12, 0, 12, 0);
        disconnectBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 52));
        disconnectBtn.setVisibility(View.GONE);
        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });
        root.addView(disconnectBtn);

        // 棋盘
        gomokuView = new GomokuView(this);
        gomokuView.setGameListener(new GomokuView.GameListener() {
            @Override
            public void onStatusChanged(final String status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(status);
                    }
                });
            }

            @Override
            public void onGameOver(final String title, final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showNetworkGameOverDialog(title, message);
                    }
                });
            }
        });
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins(0, 16, 0, 0);
        gomokuView.setLayoutParams(boardLp);
        root.addView(gomokuView);

        setContentView(root);

        networkGame = new NetworkGame(this);
    }

    private void createRoom() {
        statusText.setText("正在创建房间，等待对手加入...");
        createBtn.setEnabled(false);
        connectBtn.setEnabled(false);
        networkGame.createServer();
    }

    private void connectToServer(String ip) {
        statusText.setText("正在连接到 " + ip + "...");
        createBtn.setEnabled(false);
        connectBtn.setEnabled(false);
        networkGame.connectToServer(ip);
    }

    private void disconnect() {
        networkGame.disconnect();
    }

    @Override
    public void onConnected(String opponentName) {
        statusText.setText("已连接到: " + opponentName);
        disconnectBtn.setVisibility(View.VISIBLE);
        createBtn.setVisibility(View.GONE);
        connectBtn.setVisibility(View.GONE);
    }

    @Override
    public void onDisconnected() {
        statusText.setText("连接已断开");
        disconnectBtn.setVisibility(View.GONE);
        createBtn.setVisibility(View.VISIBLE);
        connectBtn.setVisibility(View.VISIBLE);
        createBtn.setEnabled(true);
        connectBtn.setEnabled(true);
        gomokuView.stopNetworkMode();
    }

    @Override
    public void onMoveReceived(int row, int col) {
        // 对手落子
        try {
            gomokuView.onNetworkMoveReceived(row, col);
        } catch (Exception e) {
            statusText.setText("接收落子失败: " + e.getMessage());
        }
    }

    @Override
    public void onError(String error) {
        statusText.setText("错误: " + error);
        createBtn.setEnabled(true);
        connectBtn.setEnabled(true);
    }

    @Override
    public void onGameStart(boolean isBlack) {
        this.isBlack = isBlack;
        String color = isBlack ? "黑棋" : "白棋";
        statusText.setText("游戏开始！你执" + color);

        // 启动网络对战模式
        gomokuView.startNetworkMode(networkGame, isBlack);

        if (isBlack) {
            Toast.makeText(this, "你执黑棋，请先落子", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "你执白棋，等待对手落子", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWaitingForOpponent() {
        statusText.setText("等待对手加入... 本机IP: " + NetworkGame.getLocalIpAddress());
    }

    @Override
    public void onRestartReceived() {
        showRestartConfirmDialog();
    }

    @Override
    public void onRestartAccepted() {
        statusText.setText("对方已同意，重新开始！");
        restartNetworkGame();
    }

    private void showRestartConfirmDialog() {
        LinearLayout layout = DialogHelper.createLayout(this, 36, 24, 36, 20);
        layout.addView(DialogHelper.createTitle(this, "♻️ 再来一局？", 18));
        layout.addView(DialogHelper.createMessage(this, "对方请求重新开始一局，\n是否同意？", 15));

        Button btnAgree = DialogHelper.createActionButton(this, "同意");
        Button btnRefuse = DialogHelper.createNormalButton(this, "拒绝");
        layout.addView(DialogHelper.createButtonRow(this, btnAgree, btnRefuse, 52));

        final AlertDialog dialog = DialogHelper.showDialog(this, layout, 0.80f);

        btnAgree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                networkGame.sendRestartAccept();
                restartNetworkGame();
            }
        });
        btnRefuse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void restartNetworkGame() {
        // 关闭可能还开着的胜负弹窗
        if (gameOverDialog != null) {
            gameOverDialog.dismiss();
            gameOverDialog = null;
        }
        boolean wasBlack = isBlack;
        gomokuView.startNetworkMode(networkGame, wasBlack);
        statusText.setText("新对局开始，你执" + (wasBlack ? "黑棋" : "白棋"));
    }

    private void scanLans(final Button scanBtn, final Handler uiHandler) {
        final String localIp = NetworkGame.getLocalIpAddress();
        if (localIp.equals("未知")) {
            statusText.setText("无法获取本机IP");
            scanBtn.setEnabled(true);
            scanBtn.setText("🔍 扫描局域网");
            return;
        }
        // 获取子网前缀，如 192.168.1.
        final String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());
        final int[] scanned = {0};
        final int total = 254;
        final int threads = 20; // 并发20个线程扫描

        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 1; i <= total; i++) {
            final int ipSuffix = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    String target = subnet + ipSuffix;
                    if (target.equals(localIp)) {
                        synchronized (scanned) { scanned[0]++; }
                        return;
                    }
                    Socket s = null;
                    try {
                        s = new Socket();
                        s.connect(new InetSocketAddress(target, NetworkGame.DEFAULT_PORT), 150);
                        found.add(target);
                    } catch (Exception ignored) {
                    } finally {
                        if (s != null) try { s.close(); } catch (Exception ignored) {}
                        synchronized (scanned) {
                            scanned[0]++;
                            final int progress = scanned[0];
                            if (progress % 30 == 0 || progress == total) {
                                uiHandler.post(new Runnable() {
                                    public void run() {
                                        statusText.setText("扫描中 " + progress + "/" + total + "...");
                                    }
                                });
                            }
                        }
                    }
                }
            });
        }
        pool.shutdown();
        new Thread(new Runnable() {
            public void run() {
                try {
                    pool.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                uiHandler.post(new Runnable() {
                    public void run() {
                        scanBtn.setEnabled(true);
                        scanBtn.setText("🔍 扫描局域网");
                        if (found.isEmpty()) {
                            statusText.setText("未找到五子棋房间，请确认对方已创建房间");
                            Toast.makeText(NetworkActivity.this, "未找到可连接的房间", Toast.LENGTH_SHORT).show();
                        } else {
                            statusText.setText("找到 " + found.size() + " 个房间，请选择连接");
                            showScanResultDialog(found);
                        }
                    }
                });
            }
        }).start();
    }

    private void showScanResultDialog(final List<String> ips) {
        // 构建设备列表
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.bg_dialog);
        layout.setPadding(24, 20, 24, 16);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🔍 发现 " + ips.size() + " 个房间");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(Color.rgb(62, 39, 35));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 12);
        layout.addView(tvTitle);

        for (final String ip : ips) {
            Button btn = new Button(this);
            btn.setText("📱 " + ip);
            btn.setTextSize(16);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundResource(R.drawable.bg_dialog_btn_action);
            btn.setPadding(12, 0, 12, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 48);
            lp.setMargins(0, 0, 0, 8);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ipInput.setText(ip);
                    sp.edit().putString("last_ip", ip).apply();
                    connectToServer(ip);
                }
            });
            layout.addView(btn);
        }

        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setTextSize(16);
        btnCancel.setTextColor(Color.rgb(74, 44, 26));
        btnCancel.setBackgroundResource(R.drawable.bg_dialog_btn_normal);
        btnCancel.setPadding(12, 0, 12, 0);
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48));
        layout.addView(btnCancel);

        // 关闭旧弹窗，防止泄漏
        if (scanDialog != null) { scanDialog.dismiss(); scanDialog = null; }
        scanDialog = DialogHelper.showDialog(this, layout, 0.80f);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDialog.dismiss();
                scanDialog = null;
            }
        });
    }

    private void showNetworkGameOverDialog(String title, String message) {
        LinearLayout layout = DialogHelper.createLayout(this, 36, 24, 36, 20);
        layout.addView(DialogHelper.createTitle(this, title, 22));
        layout.addView(DialogHelper.createDivider(this));
        layout.addView(DialogHelper.createMessage(this, message + "\n\n再来一局？", 16));

        Button btnRestart = DialogHelper.createActionButton(this, "再来一局");
        Button btnBack = DialogHelper.createNormalButton(this, "返回大厅");
        layout.addView(DialogHelper.createButtonRow(this, btnRestart, btnBack, 56));

        gameOverDialog = DialogHelper.showDialog(this, layout, 0.85f);

        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameOverDialog.dismiss();
                gameOverDialog = null;
                networkGame.sendRestart();
                statusText.setText("⏳ 等待对方同意...");
                Toast.makeText(NetworkActivity.this, "已发送再来一局请求，等待对方确认", Toast.LENGTH_SHORT).show();
            }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameOverDialog.dismiss();
                gameOverDialog = null;
                networkGame.disconnect();
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkGame != null) {
            networkGame.disconnect();
        }
    }
}