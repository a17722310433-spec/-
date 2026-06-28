package com.wuziqi.gomoku;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 五子棋主视图 — 负责渲染、触摸交互、音效、动画。
 *
 * 棋盘逻辑委托给 GameEngine，AI 计算委托给 AIPlayer。
 */
public class GomokuView extends View {
    public interface GameListener {
        void onStatusChanged(String status);
        void onGameOver(String title, String message);
    }

    // ===== 棋盘状态 =====
    private final GameEngine engine = new GameEngine();
    private AIPlayer aiPlayer;

    private boolean gameOver, humanTurn, pvpMode;
    private int winner;
    private int humanPiece = GameEngine.HUMAN, aiPiece = GameEngine.AI;
    private int aiLevel = 2;
    private int gameId;

    // ===== 动画状态 =====
    private int animRow = -1, animCol = -1;
    private long lastPlaceTime;
    private int hintRow = -1, hintCol = -1;
    private long hintShowTime;
    private static final long HINT_DURATION = 3500;
    private int thinkingDots;
    private boolean thinkingRunning;

    // ===== 渲染 =====
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cellSize, startX, startY, pieceRadius, boardPixelWidth;
    private int viewW, viewH;
    private boolean layoutDone;

    // ===== 音效/震动 =====
    private SoundPool soundPool;
    private int clickSnd, winSnd;
    private Vibrator vibrator;
    private boolean soundOn = true, vibrateOn = true, darkTheme, showCoords = true;

    // ===== 回放模式 =====
    private boolean replayMode;
    private int replayStep, replayTotal;

    // ===== 观战模式 =====
    private boolean watchMode, stopWatch;

    // ===== 网络对战 =====
    private boolean networkMode;
    private boolean isMyTurn;
    private int myPiece;
    private NetworkGame networkGame;

    // ===== 计时 =====
    private int timeLimitSeconds, remainingSeconds;
    private long moveStartTime;
    private Runnable timerTask;
    private int statsWins, statsLosses, statsDraws;

    // ===== 回调 =====
    private GameListener listener;

    // ===== 构造 =====
    public GomokuView(Context ctx) { super(ctx); init(ctx); }
    public GomokuView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }
    public GomokuView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(ctx); }

    private void init(Context ctx) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        clickSnd = soundPool.load(ctx, R.raw.click, 1);
        winSnd = soundPool.load(ctx, R.raw.win, 1);
        vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        aiPlayer = new AIPlayer(aiLevel, aiPiece, humanPiece);
        loadSettings();
        resetGame();
    }

    // ===== 设置接口 =====
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); loadSettings(); invalidate(); }

    public void setGameListener(GameListener l) { listener = l; notifyStatus(); }
    public void setAiLevel(int lv) {
        if (watchMode) return;
        aiLevel = Math.max(1, Math.min(4, lv));
        aiPlayer.setLevel(aiLevel);
        notifyStatus();
    }
    public int getAiLevel() { return aiLevel; }
    public String getAiLevelName() { return aiPlayer.getLevelName(); }

    public void setHumanBlack(boolean b) {
        if (watchMode) return;
        humanPiece = b ? GameEngine.HUMAN : GameEngine.AI;
        aiPiece = b ? GameEngine.AI : GameEngine.HUMAN;
        aiPlayer.setPieces(aiPiece, humanPiece);
        resetGame();
    }
    public boolean isHumanBlack() { return humanPiece == GameEngine.HUMAN; }
    public String getHumanPieceName() { return humanPiece == GameEngine.HUMAN ? "黑棋" : "白棋"; }
    public String getAiPieceName() { return aiPiece == GameEngine.HUMAN ? "黑棋" : "白棋"; }

    public void setPvpMode(boolean p) { if (watchMode) return; pvpMode = p; resetGame(); }
    public boolean isPvpMode() { return pvpMode; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public int getStatsWins() { return statsWins; }
    public int getStatsLosses() { return statsLosses; }
    public int getStatsDraws() { return statsDraws; }

    // ===== 网络对战 =====
    public void startNetworkMode(NetworkGame network, boolean isBlack) {
        networkMode = true;
        networkGame = network;
        myPiece = isBlack ? GameEngine.HUMAN : GameEngine.AI;
        humanPiece = myPiece;
        aiPiece = isBlack ? GameEngine.AI : GameEngine.HUMAN;
        isMyTurn = isBlack;
        pvpMode = true;
        watchMode = false;
        resetGame();
        humanTurn = isMyTurn;
        notifyStatus();
    }

    public void stopNetworkMode() {
        networkMode = false;
        if (networkGame != null) {
            networkGame.disconnect();
            networkGame = null;
        }
        isMyTurn = false;
        resetGame();
    }
    public boolean isNetworkMode() { return networkMode; }

    public void onNetworkMoveReceived(int row, int col) {
        int opponentPiece = (myPiece == GameEngine.HUMAN) ? GameEngine.AI : GameEngine.HUMAN;
        doPlaceMove(row, col, opponentPiece);
        playClick();
        if (doCheckEnd(row, col, opponentPiece)) return;
        isMyTurn = true;
        humanTurn = true;
        notifyStatus();
    }

    // ===== 悔棋 =====
    public void undoLastTurn() {
        if (watchMode || networkMode || engine.getHistCnt() == 0) return;
        int steps = pvpMode ? 1 : 2;
        engine.undoMove(steps);
        gameOver = false; winner = GameEngine.EMPTY;
        stopTimer();
        humanTurn = calcHumanTurn();
        notifyStatus(); invalidate();
        if (!humanTurn && !pvpMode) {
            final int id = gameId;
            postDelayed(new Runnable() { public void run() { if (id == gameId) aiMove(); } }, 300);
        }
    }

    private boolean calcHumanTurn() {
        int mc = engine.getMoveCount();
        return (mc % 2 == 0) == (humanPiece == GameEngine.HUMAN);
    }

    // ===== 提示 =====
    public void showHint() {
        if (gameOver || pvpMode || !humanTurn) return;
        final int id = gameId;
        new Thread(new Runnable() {
            public void run() {
                final int[] m = aiPlayer.findBestMove(engine.getBoard(), engine.getMoveCount());
                post(new Runnable() {
                    public void run() {
                        if (id != gameId) return;
                        if (m != null) { hintRow = m[0]; hintCol = m[1]; hintShowTime = System.currentTimeMillis(); invalidate(); }
                    }
                });
            }
        }).start();
    }

    // ===== 重置 =====
    public void resetGame() {
        stopTimer();
        stopThinking();
        engine.reset();
        gameId++;
        gameOver = false; winner = GameEngine.EMPTY;
        humanTurn = (humanPiece == GameEngine.HUMAN);
        hintRow = hintCol = -1;
        animRow = animCol = -1;
        notifyStatus(); invalidate();
        if (!humanTurn && !pvpMode) {
            final int id = gameId;
            postDelayed(new Runnable() { public void run() { if (id == gameId) aiMove(); } }, 300);
        }
    }

    // ===== 观战 =====
    public boolean isWatchMode() { return watchMode; }
    public void startWatchMode() {
        watchMode = true; stopWatch = false;
        humanPiece = GameEngine.HUMAN; aiPiece = GameEngine.AI; pvpMode = true;
        resetGame();
        watchMode = true; stopWatch = false;
        scheduleWatchMove();
    }
    public void stopWatchMode() { stopWatch = true; watchMode = false; }

    private void scheduleWatchMove() {
        final int id = gameId;
        postDelayed(new Runnable() {
            public void run() {
                if (stopWatch || gameOver || id != gameId) return;
                int role = engine.getMoveCount() % 2 == 0 ? GameEngine.HUMAN : GameEngine.AI;
                int[] m = aiPlayer.findBestMoveForRole(engine.getBoard(), engine.getMoveCount(), role);
                if (m != null) { doPlaceMove(m[0], m[1], role); playClick(); doCheckEnd(m[0], m[1], role); }
                notifyStatus();
                if (!gameOver) scheduleWatchMove();
            }
        }, 500);
    }

    // ===== 回放 =====
    public boolean isReplayMode() { return replayMode; }
    public int getReplayStep() { return replayStep; }
    public int getReplayTotal() { return replayTotal; }

    public void enterReplayMode() {
        if (engine.getHistCnt() == 0) return;
        replayMode = true;
        replayStep = 1;
        replayTotal = engine.getHistCnt();
        notifyStatus();
        invalidate();
    }

    public void exitReplayMode() {
        replayMode = false;
        notifyStatus();
        invalidate();
    }

    public void replayPrev() {
        if (!replayMode || replayStep <= 1) return;
        replayStep--;
        notifyStatus();
        invalidate();
    }

    public void replayNext() {
        if (!replayMode || replayStep >= replayTotal) return;
        replayStep++;
        notifyStatus();
        invalidate();
    }

    /** 回放模式下使用快照棋盘，否则用真实棋盘 */
    private int[][] getDisplayBoard() {
        if (replayMode) {
            return engine.getBoardSnapshot(replayStep);
        }
        return engine.getBoard();
    }

    // ===== 计时 =====
    public void setTimeLimit(int seconds) { timeLimitSeconds = seconds; }

    private void startTimer() {
        stopTimer();
        if (timeLimitSeconds <= 0) return;
        remainingSeconds = timeLimitSeconds;
        moveStartTime = System.currentTimeMillis();
        timerTask = new Runnable() {
            public void run() {
                if (gameOver || timerTask == null) return;
                long elapsed = System.currentTimeMillis() - moveStartTime;
                remainingSeconds = timeLimitSeconds - (int)(elapsed / 1000);
                if (remainingSeconds <= 0) {
                    remainingSeconds = 0; notifyStatus();
                    gameOver = true; stopThinking();
                    if (networkMode) {
                        winner = (myPiece == GameEngine.HUMAN) ? GameEngine.AI : GameEngine.HUMAN;
                        playWin();
                        if (listener != null) listener.onGameOver("⏰ 超时", "你已超时，对手获胜！");
                    } else {
                        winner = GameEngine.EMPTY;
                        if (listener != null) listener.onGameOver("⏰ 超时", "时间到！平局！");
                    }
                    notifyStatus(); invalidate(); return;
                }
                notifyStatus();
                postDelayed(timerTask, 1000);
            }
        };
        postDelayed(timerTask, 1000);
    }

    private void stopTimer() { if (timerTask != null) { removeCallbacks(timerTask); timerTask = null; } }

    // ===== 持久化 =====
    public void saveState() {
        getContext().getSharedPreferences("gomoku", Context.MODE_PRIVATE).edit()
            .putString("board", engine.encodeBoard())
            .putInt("humanPiece", humanPiece).putInt("aiLevel", aiLevel)
            .putBoolean("pvpMode", pvpMode)
            .putInt("statsW", statsWins).putInt("statsL", statsLosses).putInt("statsD", statsDraws)
            .putInt("moveCount", engine.getMoveCount())
            .putInt("lastRow", engine.getLastMove() != null ? engine.getLastMove()[0] : -1)
            .putInt("lastCol", engine.getLastMove() != null ? engine.getLastMove()[1] : -1)
            .putBoolean("gameOver", gameOver).putInt("winner", winner)
            .putString("history", engine.encodeHistory()).apply();
    }

    public boolean restoreState() {
        SharedPreferences sp = getContext().getSharedPreferences("gomoku", Context.MODE_PRIVATE);
        String b = sp.getString("board", null);
        if (b == null || b.length() < GameEngine.BOARD_SIZE * GameEngine.BOARD_SIZE) return false;
        engine.decodeBoard(b);
        humanPiece = sp.getInt("humanPiece", GameEngine.HUMAN);
        aiPiece = (humanPiece == GameEngine.HUMAN) ? GameEngine.AI : GameEngine.HUMAN;
        aiPlayer.setPieces(aiPiece, humanPiece);
        aiLevel = sp.getInt("aiLevel", 2);
        aiPlayer.setLevel(aiLevel);
        pvpMode = sp.getBoolean("pvpMode", false);
        statsWins = sp.getInt("statsW", 0); statsLosses = sp.getInt("statsL", 0); statsDraws = sp.getInt("statsD", 0);
        gameOver = sp.getBoolean("gameOver", false); winner = sp.getInt("winner", GameEngine.EMPTY);
        String historyStr = sp.getString("history", null);
        if (historyStr != null && !historyStr.isEmpty()) engine.decodeHistory(historyStr);
        humanTurn = calcHumanTurn();
        stopTimer();
        stopThinking();
        notifyStatus(); invalidate();
        if (!pvpMode && !gameOver && !humanTurn) {
            final int id = gameId;
            postDelayed(new Runnable() { public void run() { if (id == gameId) aiMove(); } }, 300);
        }
        return true;
    }

    // ===== 设置 =====
    public void reloadSettings() { loadSettings(); invalidate(); }
    private void loadSettings() {
        SharedPreferences sp = getContext().getSharedPreferences("gomoku_settings", Context.MODE_PRIVATE);
        soundOn = sp.getBoolean("sound", true);
        vibrateOn = sp.getBoolean("vibrate", true);
        darkTheme = sp.getBoolean("dark", false);
        showCoords = sp.getBoolean("coords", true);
        SharedPreferences gp = getContext().getSharedPreferences("gomoku", Context.MODE_PRIVATE);
        statsWins = gp.getInt("statsW", statsWins);
        statsLosses = gp.getInt("statsL", statsLosses);
        statsDraws = gp.getInt("statsD", statsDraws);
        setBackgroundColor(darkTheme ? Color.rgb(50, 50, 50) : Color.rgb(246, 220, 150));
        textPaint.setColor(darkTheme ? Color.rgb(200, 200, 200) : Color.rgb(90, 60, 25));
    }

    // ===== 音效 =====
    private void playClick() {
        if (!soundOn || clickSnd == 0) return;
        try { soundPool.play(clickSnd, 0.8f, 0.8f, 1, 0, 1.0f); } catch (Exception ignored) {}
    }
    private void playWin() {
        if (soundOn && winSnd != 0) try { soundPool.play(winSnd, 1.0f, 1.0f, 1, 0, 1.0f); } catch (Exception ignored) {}
        if (vibrateOn) try { if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(150); } catch (Exception ignored) {}
    }

    // ===== 着子（统一入口）=====
    private void doPlaceMove(int row, int col, int piece) {
        engine.placeMove(row, col, piece);
        animRow = row; animCol = col; lastPlaceTime = System.currentTimeMillis();
        startTimer();
        invalidate();
    }

    // ===== AI =====
    private void aiMove() {
        if (gameOver || (pvpMode && !watchMode)) return;
        final int piece = aiPiece;
        final int id = gameId;
        new Thread(new Runnable() {
            public void run() {
                final int[] m = aiPlayer.findBestMove(engine.getBoard(), engine.getMoveCount());
                post(new Runnable() {
                    public void run() {
                        if (gameOver || id != gameId) return;
                        if (m != null) { doPlaceMove(m[0], m[1], piece); playClick(); if (doCheckEnd(m[0], m[1], piece)) return; }
                        humanTurn = true; startTimer(); notifyStatus();
                    }
                });
            }
        }).start();
    }

    // ===== 胜负判定 =====
    private boolean doCheckEnd(int row, int col, int role) {
        if (engine.checkWin(row, col, role)) {
            stopTimer();
            gameOver = true; winner = role;
            stopThinking();
            if (!watchMode) {
                if (role == humanPiece) statsWins++;
                else if (role == aiPiece && !pvpMode) statsLosses++;
            }
            playWin(); notifyStatus(); invalidate();
            if (listener != null) {
                String title, msg;
                if (watchMode) {
                    title = "观战结束"; msg = pieceName(role) + " 获胜！";
                } else if (networkMode) {
                    if (role == myPiece) { title = "🎉 你赢了！"; msg = "恭喜，击败了对手！"; }
                    else if (role == GameEngine.EMPTY) { title = "🤝 平局"; msg = "势均力敌！"; }
                    else { title = "😞 你输了"; msg = "别灰心，再接再厉！"; }
                } else if (pvpMode) {
                    title = pieceName(role) + " 获胜！"; msg = "精彩的对局！";
                } else {
                    if (role == humanPiece) { title = "🎉 你赢了！"; msg = "太棒了！AI不是你的对手！"; }
                    else { title = "😞 你输了"; msg = "AI获胜了，再来一局吧！"; }
                }
                listener.onGameOver(title, msg);
            }
            return true;
        }
        if (engine.isBoardFull()) {
            stopTimer();
            gameOver = true; winner = GameEngine.EMPTY; statsDraws++;
            stopThinking(); notifyStatus(); invalidate();
            if (listener != null) listener.onGameOver("🤝 平局", "棋盘已满，势均力敌！");
            return true;
        }
        return false;
    }

    // ===== 思考动画 =====
    private void startThinking() { thinkingRunning = true; thinkingDots = 0; scheduleDots(); }
    private void stopThinking() { thinkingRunning = false; }

    private void scheduleDots() {
        if (!thinkingRunning || gameOver || humanTurn) return;
        thinkingDots = (thinkingDots + 1) % 4;
        if (listener != null && !gameOver && !humanTurn) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < thinkingDots; i++) sb.append(".");
            listener.onStatusChanged("AI 思考中" + sb + "：" + getAiPieceName() + "　强度：" + getAiLevelName());
        }
        postDelayed(new Runnable() { public void run() { scheduleDots(); } }, 1000);
    }

    // ===== 状态通知 =====
    private void notifyStatus() {
        if (listener == null) return;
        if (replayMode) {
            listener.onStatusChanged("📽 回放 " + replayStep + "/" + replayTotal + "  ◀ 上一步  ▶ 下一步");
            return;
        }
        String timerInfo = (timeLimitSeconds > 0 && !gameOver) ? " ⏱" + remainingSeconds + "s" : "";
        if (gameOver) {
            if (watchMode) listener.onStatusChanged("观战结束 · " + pieceName(winner) + " 获胜！" + timerInfo);
            else if (networkMode) {
                if (winner == myPiece) listener.onStatusChanged("🎉 你赢了！" + timerInfo);
                else if (winner == GameEngine.EMPTY) listener.onStatusChanged("平局！" + timerInfo);
                else listener.onStatusChanged("对手获胜！" + timerInfo);
            }
            else if (pvpMode) listener.onStatusChanged(pieceName(winner) + " 获胜！" + timerInfo);
            else if (winner == GameEngine.EMPTY) listener.onStatusChanged("平局！" + timerInfo);
            else if (winner == humanPiece) listener.onStatusChanged("🎉 你赢了！" + timerInfo);
            else listener.onStatusChanged("AI 获胜！" + timerInfo);
        } else if (watchMode)
            listener.onStatusChanged("观战中 · " + pieceName(engine.getMoveCount() % 2 == 0 ? GameEngine.HUMAN : GameEngine.AI) + " 走棋");
        else if (networkMode) {
            if (isMyTurn) listener.onStatusChanged("轮到你：" + pieceName(myPiece) + timerInfo);
            else listener.onStatusChanged("等待对手落子..." + timerInfo);
        }
        else if (pvpMode)
            listener.onStatusChanged("双人模式 · " + pieceName(engine.getMoveCount() % 2 == 0 ? humanPiece : aiPiece) + " 走棋");
        else if (humanTurn)
            listener.onStatusChanged("轮到你：" + getHumanPieceName() + timerInfo);
    }

    private String pieceName(int role) { return role == GameEngine.HUMAN ? "黑棋" : "白棋"; }

    // ===== 布局 =====
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); viewW = w; viewH = h; layoutDone = false;
    }

    // ===== 触摸 =====
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (watchMode || replayMode) return true;
        if (!layoutDone) return true;
        if (networkMode && !isMyTurn) return true;
        if (!networkMode && (gameOver || (!humanTurn && !pvpMode))) return true;
        if (networkMode && gameOver) return true;

        int col = Math.round((event.getX() - startX) / cellSize);
        int row = Math.round((event.getY() - startY) / cellSize);

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!engine.inside(row, col) || !engine.isEmpty(row, col)) return true;
            hintRow = hintCol = -1;
            int piece;
            if (networkMode) {
                piece = myPiece;
            } else {
                piece = pvpMode ? (engine.getMoveCount() % 2 == 0 ? humanPiece : aiPiece) : humanPiece;
            }

            doPlaceMove(row, col, piece);
            playClick();

            if (networkMode) {
                if (networkGame != null) {
                    try {
                        networkGame.sendMove(row, col);
                        isMyTurn = false;
                        humanTurn = false;
                    } catch (Exception e) {
                        // 发送失败：撤回本地落子
                        engine.undoMove(1);
                        if (listener != null) listener.onStatusChanged("⚠️ 发送失败，请重试");
                        invalidate();
                        return true;
                    }
                }
                notifyStatus();
                if (doCheckEnd(row, col, piece)) return true;
                return true;
            } else if (doCheckEnd(row, col, piece)) return true;

            if (pvpMode) { notifyStatus(); } else {
                stopTimer();
                humanTurn = false; notifyStatus(); startThinking();
                final int id = gameId;
                postDelayed(new Runnable() { public void run() { stopThinking(); if (id == gameId) aiMove(); } }, 280);
            }
            return true;
        }
        return true;
    }

    // ===== 渲染 =====
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!layoutDone) {
            cellSize = Math.min(viewW / (GameEngine.BOARD_SIZE + 1.5f), viewH / (GameEngine.BOARD_SIZE + 2.5f));
            boardPixelWidth = cellSize * (GameEngine.BOARD_SIZE - 1);
            startX = (viewW - boardPixelWidth) / 2f;
            startY = cellSize * 1.2f;
            pieceRadius = cellSize * 0.42f;
            layoutDone = true;
        }
        drawBoard(canvas);
        drawPieces(canvas);
        drawLastMoveMarker(canvas);
        drawHintMarker(canvas);
        drawWinLine(canvas);
        drawStep(canvas);
        drawHintText(canvas);
        drawReplayOverlay(canvas);
    }

    private void drawBoard(Canvas canvas) {
        paint.setShader(null);
        paint.setStrokeWidth(Math.max(1.8f, cellSize / 25f));
        paint.setColor(darkTheme ? Color.rgb(180, 150, 110) : Color.rgb(90, 58, 18));
        float o = cellSize * 0.06f;
        float xEnd = startX + boardPixelWidth;
        float yEnd = startY + boardPixelWidth;
        for (int i = 0; i < GameEngine.BOARD_SIZE; i++) {
            float ly = startY + i * cellSize;
            float lx = startX + i * cellSize;
            canvas.drawLine(i == 0 ? startX - o : startX, ly,
                    i == GameEngine.BOARD_SIZE - 1 ? xEnd + o : xEnd, ly, paint);
            canvas.drawLine(lx, i == 0 ? startY - o : startY,
                    lx, i == GameEngine.BOARD_SIZE - 1 ? yEnd + o : yEnd, paint);
        }
        paint.setColor(darkTheme ? Color.rgb(160, 130, 95) : Color.rgb(80, 45, 10));
        int[] stars = {3, 7, 11};
        for (int r : stars) for (int c : stars)
            canvas.drawCircle(startX + c * cellSize, startY + r * cellSize, cellSize * 0.10f, paint);
        if (showCoords) {
            textPaint.setTextSize(Math.max(10f, cellSize * 0.38f));
            textPaint.setColor(darkTheme ? Color.rgb(180, 150, 120) : Color.rgb(100, 70, 40));
            float labelY = startY - cellSize * 0.55f;
            float labelX = startX - cellSize * 0.80f;
            for (int i = 0; i < GameEngine.BOARD_SIZE; i++) {
                canvas.drawText(String.valueOf((char)('A' + i)), startX + i * cellSize, labelY, textPaint);
                canvas.drawText(String.valueOf(i + 1), labelX, startY + i * cellSize + cellSize * 0.14f, textPaint);
            }
        }
    }

    private void drawPieces(Canvas canvas) {
        int[][] board = getDisplayBoard();
        long now = System.currentTimeMillis();
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] == GameEngine.EMPTY) continue;
                float s = 1f;
                if (!replayMode && r == animRow && c == animCol) {
                    long e = now - lastPlaceTime;
                    s = Math.min(1f, e / 160f);
                    if (s < 0.05f) s = 0.05f;
                    if (e > 220) animRow = animCol = -1;
                }
                drawPiece(canvas, r, c, board[r][c], s);
            }
        }
    }

    private void drawPiece(Canvas canvas, int row, int col, int type, float scale) {
        float x = startX + col * cellSize, y = startY + row * cellSize, r = pieceRadius * scale;
        if (r < 0.5f) return;
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        boolean isHuman = (type == GameEngine.HUMAN);
        paint.setColor(isHuman ? Color.rgb(45, 45, 45) : (darkTheme ? Color.rgb(230, 230, 230) : Color.rgb(195, 195, 195)));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, 1.5f * scale));
        paint.setColor(isHuman ? Color.BLACK : (darkTheme ? Color.rgb(180, 180, 180) : Color.rgb(140, 140, 140)));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.FILL);
        int st = engine.getStepAt(row, col);
        if (st > 0 && r > cellSize * 0.28f) {
            float ts = Math.max(8f, r * 0.72f); textPaint.setTextSize(ts);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(isHuman ? Color.rgb(180, 180, 180) : Color.rgb(60, 60, 60));
            canvas.drawText(String.valueOf(st), x, y + ts * 0.35f, textPaint);
        }
    }

    private void drawLastMoveMarker(Canvas canvas) {
        if (replayMode) return; // 回放模式下不画最后落子标记
        int[] last = engine.getLastMove();
        if (last == null) return;
        int lr = last[0], lc = last[1];
        float x = startX + lc * cellSize, y = startY + lr * cellSize, sc = 1f;
        if (lr == animRow && lc == animCol) {
            long e = System.currentTimeMillis() - lastPlaceTime;
            sc = Math.min(1f, e / 160f);
            if (sc < 0.05f) sc = 0.05f;
        }
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, cellSize / 16f)); paint.setColor(Color.rgb(220, 45, 45));
        canvas.drawCircle(x, y, pieceRadius * sc * 0.42f, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void drawHintMarker(Canvas canvas) {
        if (hintRow < 0) return;
        long e = System.currentTimeMillis() - hintShowTime;
        if (e > HINT_DURATION) { hintRow = hintCol = -1; return; }
        float a = (e % 600) < 300 ? 0.75f : 0.20f;
        float x = startX + hintCol * cellSize, y = startY + hintRow * cellSize;
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int)(a * 255), 50, 200, 80));
        canvas.drawCircle(x, y, pieceRadius * 0.62f, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
        paint.setColor(Color.argb((int)(a * 255), 30, 180, 60));
        canvas.drawCircle(x, y, pieceRadius * 0.67f, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void drawWinLine(Canvas canvas) {
        // 回放模式下不画获胜红线（除非在最后一步）
        if (replayMode && replayStep < replayTotal) return;
        int ws = engine.getWinStartRow();
        if (ws < 0) return;
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(6f, cellSize * 0.15f)); paint.setColor(Color.rgb(230, 40, 35));
        canvas.drawLine(startX + engine.getWinStartCol() * cellSize, startY + engine.getWinStartRow() * cellSize,
                startX + engine.getWinEndCol() * cellSize, startY + engine.getWinEndRow() * cellSize, paint);
        paint.setStrokeCap(Paint.Cap.BUTT); paint.setStyle(Paint.Style.FILL);
    }

    private void drawStep(Canvas canvas) {
        textPaint.setTextSize(Math.max(10f, cellSize * 0.35f));
        textPaint.setColor(darkTheme ? Color.rgb(170, 140, 110) : Color.rgb(130, 80, 40));
        canvas.drawText("#" + engine.getMoveCount(), startX + boardPixelWidth,
                startY + boardPixelWidth + cellSize * 0.80f, textPaint);
    }

    private void drawHintText(Canvas canvas) {
        if (engine.getMoveCount() > 0) return;
        textPaint.setTextSize(Math.max(22f, cellSize * 0.48f));
        textPaint.setColor(darkTheme ? Color.rgb(180, 150, 120) : Color.rgb(100, 65, 30));
        canvas.drawText("点击交叉点落子", getWidth() / 2f, getHeight() - cellSize * 0.20f, textPaint);
    }

    private void drawReplayOverlay(Canvas canvas) {
        if (!replayMode) return;
        // 顶部黑色半透明条
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), cellSize * 0.9f, paint);
        // 回放文字
        textPaint.setTextSize(cellSize * 0.4f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.WHITE);
        String info = "📽 回放 " + replayStep + "/" + replayTotal;
        canvas.drawText(info, cellSize * 0.3f, cellSize * 0.6f, textPaint);
        // 操作提示
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(cellSize * 0.28f);
        textPaint.setColor(Color.argb(200, 255, 255, 200));
        canvas.drawText("◀ ▶ 步进     ✕ 退出", getWidth() - cellSize * 0.3f, cellSize * 0.6f, textPaint);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        stopTimer();
    }
}
