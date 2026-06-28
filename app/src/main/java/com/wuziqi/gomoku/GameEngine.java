package com.wuziqi.gomoku;

/**
 * 五子棋棋盘引擎 — 管理棋盘状态、落子、悔棋、历史记录、序列化。
 */
public class GameEngine {

    public static final int BOARD_SIZE = 15;
    public static final int EMPTY = 0, HUMAN = 1, AI = 2, WIN_COUNT = 5;
    public static final int[] CENTER = {BOARD_SIZE / 2, BOARD_SIZE / 2};
    static final int[][] DIRS = {{1,0},{0,1},{1,1},{1,-1}};

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private final int[] histR = new int[BOARD_SIZE * BOARD_SIZE];
    private final int[] histC = new int[BOARD_SIZE * BOARD_SIZE];
    private final int[] histP = new int[BOARD_SIZE * BOARD_SIZE];
    private final int[][] stepAt = new int[BOARD_SIZE][BOARD_SIZE];
    private int histCnt;
    private int moveCount;

    private int winStartRow = -1, winStartCol = -1;
    private int winEndRow = -1, winEndCol = -1;

    public int[][] getBoard() { return board; }
    public int getStepAt(int r, int c) { return stepAt[r][c]; }
    public int getPieceAt(int r, int c) { return board[r][c]; }
    public int getHistCnt() { return histCnt; }
    public int getMoveCount() { return moveCount; }
    public int[] getLastMove() {
        return histCnt > 0 ? new int[]{histR[histCnt - 1], histC[histCnt - 1], histP[histCnt - 1]} : null;
    }
    public int getWinStartRow() { return winStartRow; }
    public int getWinStartCol() { return winStartCol; }
    public int getWinEndRow() { return winEndRow; }
    public int getWinEndCol() { return winEndCol; }
    public boolean isEmpty(int r, int c) { return board[r][c] == EMPTY; }

    public boolean placeMove(int row, int col, int piece) {
        if (!inside(row, col) || board[row][col] != EMPTY) return false;
        board[row][col] = piece;
        histR[histCnt] = row;
        histC[histCnt] = col;
        histP[histCnt] = piece;
        stepAt[row][col] = ++histCnt;
        moveCount = histCnt;
        return true;
    }

    public boolean undoMove(int steps) {
        if (histCnt == 0 || steps <= 0) return false;
        for (int i = 0; i < steps && histCnt > 0; i++) {
            histCnt--;
            board[histR[histCnt]][histC[histCnt]] = EMPTY;
            stepAt[histR[histCnt]][histC[histCnt]] = 0;
        }
        moveCount = histCnt;
        winStartRow = winStartCol = winEndRow = winEndCol = -1;
        return true;
    }

    public void reset() {
        for (int r = 0; r < BOARD_SIZE; r++)
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = EMPTY;
                stepAt[r][c] = 0;
            }
        histCnt = 0;
        moveCount = 0;
        winStartRow = winStartCol = winEndRow = winEndCol = -1;
    }

    public boolean checkWin(int row, int col, int role) {
        for (int[] d : DIRS) {
            int cnt = 1;
            int r = row + d[0], c = col + d[1];
            while (inside(r, c) && board[r][c] == role) { cnt++; r += d[0]; c += d[1]; }
            r = row - d[0]; c = col - d[1];
            while (inside(r, c) && board[r][c] == role) { cnt++; r -= d[0]; c -= d[1]; }
            if (cnt >= WIN_COUNT) {
                int sr = row, sc = col;
                while (inside(sr - d[0], sc - d[1]) && board[sr - d[0]][sc - d[1]] == role) { sr -= d[0]; sc -= d[1]; }
                winStartRow = sr; winStartCol = sc;
                winEndRow = sr + d[0] * (WIN_COUNT - 1);
                winEndCol = sc + d[1] * (WIN_COUNT - 1);
                return true;
            }
        }
        return false;
    }

    public boolean isBoardFull() {
        return moveCount >= BOARD_SIZE * BOARD_SIZE;
    }

    public String encodeBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < BOARD_SIZE; r++)
            for (int c = 0; c < BOARD_SIZE; c++)
                sb.append(board[r][c] == HUMAN ? '1' : board[r][c] == AI ? '2' : '0');
        return sb.toString();
    }

    public void decodeBoard(String s) {
        if (s == null || s.length() < BOARD_SIZE * BOARD_SIZE) return;
        for (int r = 0; r < BOARD_SIZE; r++)
            for (int c = 0; c < BOARD_SIZE; c++) {
                char ch = s.charAt(r * BOARD_SIZE + c);
                board[r][c] = ch == '1' ? HUMAN : ch == '2' ? AI : EMPTY;
            }
    }

    public String encodeHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < histCnt; i++)
            sb.append(histR[i]).append(",").append(histC[i]).append(",").append(histP[i]).append(";");
        return sb.toString();
    }

    public boolean decodeHistory(String s) {
        if (s == null || s.isEmpty()) return true;
        String[] parts = s.split(";");
        if (parts.length > histR.length) { reset(); return false; }
        histCnt = Math.min(parts.length, histR.length);
        try {
            for (int i = 0; i < histCnt; i++) {
                String[] fields = parts[i].split(",");
                if (fields.length >= 3) {
                    int r = Integer.parseInt(fields[0]);
                    int c = Integer.parseInt(fields[1]);
                    int p = Integer.parseInt(fields[2]);
                    if (!inside(r, c) || (p != HUMAN && p != AI)) throw new NumberFormatException();
                    histR[i] = r; histC[i] = c; histP[i] = p;
                    stepAt[r][c] = i + 1;
                    board[r][c] = p;
                }
            }
        } catch (NumberFormatException e) {
            reset();
            return false;
        }
        moveCount = histCnt;
        return true;
    }

    /** 获取前 N 步的棋盘快照（用于回放） */
    public int[][] getBoardSnapshot(int steps) {
        int[][] snap = new int[BOARD_SIZE][BOARD_SIZE];
        int limit = Math.min(steps, histCnt);
        for (int i = 0; i < limit; i++) {
            snap[histR[i]][histC[i]] = histP[i];
        }
        return snap;
    }

    public static boolean inside(int r, int c) {
        return r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE;
    }
}
