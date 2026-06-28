package com.wuziqi.gomoku;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkGame {
    private static final String TAG = "NetworkGame";
    public static final int DEFAULT_PORT = 12345;
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long HEARTBEAT_TIMEOUT_MS = 15000;

    public interface NetworkListener {
        void onConnected(String opponentName);
        void onDisconnected();
        void onMoveReceived(int row, int col);
        void onError(String error);
        void onGameStart(boolean isBlack);
        void onWaitingForOpponent();
        void onRestartReceived();
        void onRestartAccepted();
    }

    private final NetworkListener listener;
    private final Handler handler;
    private final ExecutorService sendPool;
    private final int port;

    private ServerSocket serverSocket;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean isRunning;
    private final AtomicBoolean disconnecting = new AtomicBoolean(false);
    private String opponentName;
    private long lastHeartbeatRcvd;
    private Runnable heartbeatChecker;
    private Runnable heartbeatSender;

    public NetworkGame(NetworkListener listener) {
        this(listener, DEFAULT_PORT);
    }

    public NetworkGame(NetworkListener listener, int port) {
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.sendPool = Executors.newSingleThreadExecutor();
        this.port = port;
    }

    public int getPort() { return port; }

    public void createServer() {
        cleanup();
        disconnecting.set(false);
        isRunning = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onWaitingForOpponent(); } });
                    socket = serverSocket.accept();
                    setupStreams();
                    sendName(android.os.Build.MODEL);
                    receiveName();
                    handler.post(new Runnable() {
                        public void run() { if (listener != null) { listener.onConnected(opponentName); listener.onGameStart(true); } }
                    });
                    startHeartbeat();
                    listenForMoves();
                } catch (final IOException e) {
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onError("服务器错误: " + e.getMessage()); } });
                }
            }
        }).start();
    }

    public void connectToServer(final String host) {
        connectToServer(host, port);
    }

    public void connectToServer(final String host, final int targetPort) {
        cleanup();
        disconnecting.set(false);
        isRunning = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, targetPort), 3000);
                    setupStreams();
                    receiveName();
                    sendName(android.os.Build.MODEL);
                    handler.post(new Runnable() {
                        public void run() { if (listener != null) { listener.onConnected(opponentName); listener.onGameStart(false); } }
                    });
                    startHeartbeat();
                    listenForMoves();
                } catch (final IOException e) {
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onError("连接失败: " + e.getMessage()); } });
                }
            }
        }).start();
    }

    private void setupStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    private void sendName(String name) { out.println("NAME:" + name); }

    private void receiveName() throws IOException {
        String line = in.readLine();
        if (line != null && line.startsWith("NAME:")) opponentName = line.substring(5);
    }

    private void startHeartbeat() {
        lastHeartbeatRcvd = System.currentTimeMillis();
        heartbeatSender = new Runnable() {
            public void run() {
                if (!isRunning) return;
                sendPool.execute(new Runnable() {
                    public void run() { if (out != null && isRunning) { try { out.println("PING"); } catch (Exception ignored) {} } }
                });
                handler.postDelayed(heartbeatSender, HEARTBEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(heartbeatSender, HEARTBEAT_INTERVAL_MS);
        heartbeatChecker = new Runnable() {
            public void run() {
                if (!isRunning) return;
                if (System.currentTimeMillis() - lastHeartbeatRcvd > HEARTBEAT_TIMEOUT_MS) {
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onError("连接超时，对手已断开"); } });
                    disconnect();
                    return;
                }
                handler.postDelayed(heartbeatChecker, HEARTBEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(heartbeatChecker, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatSender != null) handler.removeCallbacks(heartbeatSender);
        if (heartbeatChecker != null) handler.removeCallbacks(heartbeatChecker);
        heartbeatSender = null;
        heartbeatChecker = null;
    }

    private void listenForMoves() {
        try {
            while (isRunning) {
                String line = in.readLine();
                if (line == null) break;
                if (line.equals("PING")) {
                    sendPool.execute(new Runnable() {
                        public void run() { if (out != null && isRunning) { try { out.println("PONG"); } catch (Exception ignored) {} } }
                    });
                    continue;
                }
                if (line.equals("PONG")) { lastHeartbeatRcvd = System.currentTimeMillis(); continue; }
                if (line.startsWith("MOVE:")) {
                    String[] parts = line.substring(5).split(",");
                    if (parts.length == 2) {
                        final int row = Integer.parseInt(parts[0]);
                        final int col = Integer.parseInt(parts[1]);
                        handler.post(new Runnable() { public void run() { if (listener != null) listener.onMoveReceived(row, col); } });
                    }
                } else if (line.equals("RESTART")) {
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onRestartReceived(); } });
                } else if (line.equals("ACCEPT")) {
                    handler.post(new Runnable() { public void run() { if (listener != null) listener.onRestartAccepted(); } });
                } else if (line.equals("QUIT")) break;
            }
        } catch (IOException e) {
            Log.e(TAG, "接收错误", e);
        } finally {
            disconnect();
        }
    }

    public void sendMove(final int row, final int col) {
        sendPool.execute(new Runnable() {
            public void run() {
                if (out != null && isRunning) { try { out.println("MOVE:" + row + "," + col); } catch (Exception e) { disconnect(); } }
            }
        });
    }

    public void sendRestart() {
        sendPool.execute(new Runnable() {
            public void run() { if (out != null) { try { out.println("RESTART"); } catch (Exception ignored) {} } }
        });
    }

    public void sendRestartAccept() {
        sendPool.execute(new Runnable() {
            public void run() { if (out != null) { try { out.println("ACCEPT"); } catch (Exception ignored) {} } }
        });
    }

    private void cleanup() {
        stopHeartbeat();
        try {
            if (out != null) { out.close(); out = null; }
            if (in != null) { in.close(); in = null; }
            if (socket != null) { socket.close(); socket = null; }
            if (serverSocket != null) { serverSocket.close(); serverSocket = null; }
        } catch (IOException ignored) {}
    }

    public void disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return;
        isRunning = false;
        sendPool.execute(new Runnable() {
            public void run() {
                cleanup();
                handler.post(new Runnable() {
                    public void run() {
                        disconnecting.set(false);
                        if (listener != null) listener.onDisconnected();
                    }
                });
            }
        });
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) return addr.getHostAddress();
                }
            }
        } catch (Exception e) { Log.e(TAG, "获取IP失败", e); }
        return "未知";
    }
}
