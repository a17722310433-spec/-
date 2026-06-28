package com.wuziqi.gomoku;

import java.util.Random;

/**
 * 五子棋 AI 引擎 — 独立于 UI，支持四级难度。
 *
 * 难度说明：
 *   1 - 简单：70% 随机 + 基本评估
 *   2 - 普通：单层评估，攻击为主
 *   3 - 困难：单层评估 + 威胁检测
 *   4 - 大师：2层 Minimax + Alpha-Beta 剪枝
 */
public class AIPlayer {

    private static final int SCORE_FIVE       = 1000000;
    private static final int SCORE_LIVE_FOUR  = 100000;
    private static final int SCORE_HALF_FOUR  = 20000;
    private static final int SCORE_LIVE_THREE = 9000;
    private static final int SCORE_HALF_THREE = 1200;
    private static final int SCORE_LIVE_TWO   = 600;
    private static final int SCORE_HALF_TWO   = 120;
    private static final int SCORE_LIVE_ONE   = 30;
    private static final int SCORE_DEAD       = 5;

    private static final Random RANDOM = new Random();

    // 开局库：前几步推荐位置（相对中心7,7的偏移）
    private static final int[][] OPENING_AI = {
            {0,0}, {1,0}, {-1,0}, {0,1}, {0,-1},
            {1,1}, {1,-1}, {-1,1}, {-1,-1},
    };
    private static final int[][] OPENING_DEF = {
            {0,0}, {1,0}, {1,1},
    };

    private int aiLevel;
    private int aiPiece;
    private int humanPiece;

    public AIPlayer(int level, int aiPiece, int humanPiece) {
        this.aiLevel = level;
        this.aiPiece = aiPiece;
        this.humanPiece = humanPiece;
    }

    public void setLevel(int level) { this.aiLevel = level; }
    public void setPieces(int aiPiece, int humanPiece) {
        this.aiPiece = aiPiece;
        this.humanPiece = humanPiece;
    }
    public int getLevel() { return aiLevel; }
    public int getAiPiece() { return aiPiece; }
    public int getHumanPiece() { return humanPiece; }
    public String getLevelName() {
        switch (aiLevel) {
            case 1: return "简单";
            case 2: return "普通";
            case 3: return "困难";
            case 4: return "大师";
            default: return "普通";
        }
    }

    public int[] findBestMove(int[][] board, int moveCount) {
        if (moveCount == 0) return GameEngine.CENTER.clone();

        // 前6步使用开局库
        if (aiLevel >= 2 && moveCount <= 6) {
            int[] book = findOpeningMove(board, moveCount);
            if (book != null) return book;
        }

        switch (aiLevel) {
            case 1: return findEasyMove(board, moveCount);
            case 4: return findBestMoveMinimax(board, moveCount);
            default: return findBestMoveInternal(board, moveCount);
        }
    }

    public int[] findBestMoveForRole(int[][] board, int moveCount, int role) {
        if (moveCount == 0) return GameEngine.CENTER.clone();
        int savedAi = aiPiece, savedHuman = humanPiece;
        aiPiece = role;
        humanPiece = (role == GameEngine.HUMAN) ? GameEngine.AI : GameEngine.HUMAN;
        int[] result = findBestMove(board, moveCount);
        aiPiece = savedAi;
        humanPiece = savedHuman;
        return result;
    }

    public int[] findWinLine(int[][] board, int row, int col, int role) {
        for (int[] d : GameEngine.DIRS) {
            int sr = row, sc = col;
            while (GameEngine.inside(sr - d[0], sc - d[1]) && board[sr - d[0]][sc - d[1]] == role) { sr -= d[0]; sc -= d[1]; }
            int er = row, ec = col;
            while (GameEngine.inside(er + d[0], ec + d[1]) && board[er + d[0]][ec + d[1]] == role) { er += d[0]; ec += d[1]; }
            if (Math.max(Math.abs(er - sr), Math.abs(ec - sc)) + 1 >= GameEngine.WIN_COUNT) {
                return new int[]{sr, sc, sr + d[0] * (GameEngine.WIN_COUNT - 1), sc + d[1] * (GameEngine.WIN_COUNT - 1)};
            }
        }
        return null;
    }

    private int[] findOpeningMove(int[][] board, int moveCount) {
        boolean aiFirst = board[7][7] == GameEngine.EMPTY || board[7][7] == aiPiece;
        int[][] openings = aiFirst ? OPENING_AI : OPENING_DEF;
        int maxLookup = Math.min(moveCount / 2 + 2, openings.length);
        for (int i = 0; i < maxLookup; i++) {
            int r = 7 + openings[i][0];
            int c = 7 + openings[i][1];
            if (board[r][c] == GameEngine.EMPTY) {
                if (moveCount <= 1 || hasNeighbor(board, r, c, 2)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private int[] findBestMoveInternal(int[][] board, int moveCount) {
        java.util.ArrayList<int[]> candidates = new java.util.ArrayList<>(128);
        int bestScore = Integer.MIN_VALUE;
        long deadline = System.nanoTime() + 250_000_000L;

        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] != GameEngine.EMPTY || !hasNeighbor(board, r, c, 2)) continue;
                if (System.nanoTime() > deadline && !candidates.isEmpty()) break;

                if (wouldWin(board, r, c, aiPiece)) return new int[]{r, c};
                int atk = evaluatePosition(board, r, c, aiPiece);

                if (wouldWin(board, r, c, humanPiece)) return new int[]{r, c};
                int def = evaluatePosition(board, r, c, humanPiece);

                int centerBonus = 14 - (Math.abs(r - 7) + Math.abs(c - 7));
                int score;

                if (aiLevel == 2) {
                    score = atk + def * 3 / 4 + centerBonus;
                } else {
                    int ctdAtk = countThreats(board, r, c, aiPiece);
                    int ctdDef = countThreats(board, r, c, humanPiece);
                    score = atk + def * 11 / 10 + centerBonus
                            + ctdAtk * 4500 + ctdDef * 5200
                            + atk / 4 + def / 3;
                }

                if (score > bestScore) {
                    bestScore = score;
                    candidates.clear();
                }
                if (score >= bestScore - 1) {
                    candidates.add(new int[]{r, c, score});
                }
            }
            if (System.nanoTime() > deadline && !candidates.isEmpty()) break;
        }

        if (candidates.isEmpty()) return findRandomMove(board, moveCount);

        int actualCnt = 0;
        for (int[] cand : candidates) if (cand[2] >= bestScore - 1) actualCnt++;
        int pick = RANDOM.nextInt(actualCnt);
        int idx = 0;
        for (int[] cand : candidates) {
            if (cand[2] >= bestScore - 1) {
                if (idx == pick) return new int[]{cand[0], cand[1]};
                idx++;
            }
        }
        return new int[]{candidates.get(0)[0], candidates.get(0)[1]};
    }

    // ===== 第4级·大师：Minimax + Alpha-Beta =====

    /**
     * 大师级走法：迭代加深搜索
     *
     * 从2层开始，逐步加深到最大深度。
     * 浅层先给出一个可用走法，深层搜索如果超时就回退到浅层结果。
     */
    private int[] findBestMoveMinimax(int[][] board, int moveCount) {
        // 先快速检查直接获胜或必须堵的位置
        int[] immediate = findImmediateMove(board);
        if (immediate != null) return immediate;

        // 生成候选列表
        java.util.ArrayList<int[]> candidates = new java.util.ArrayList<>();
        long deadline = System.nanoTime() + 250_000_000L;

        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] != GameEngine.EMPTY || !hasNeighbor(board, r, c, 2)) continue;
                int atk = evaluatePosition(board, r, c, aiPiece);
                int def = evaluatePosition(board, r, c, humanPiece);
                candidates.add(new int[]{r, c, atk + def * 2});
            }
        }

        java.util.Collections.sort(candidates, new java.util.Comparator<int[]>() {
            public int compare(int[] a, int[] b) { return b[2] - a[2]; }
        });
        int limit = Math.min(15, candidates.size());
        if (limit == 0) return findRandomMove(board, moveCount);

        // ===== 迭代加深 =====
        // 先保存浅层结果（2层，保证有可用的走法）
        int bestR = candidates.get(0)[0], bestC = candidates.get(0)[1];
        int bestScore = Integer.MIN_VALUE;

        // 从2层开始，逐渐加深到6层
        for (int maxDepth = 2; maxDepth <= 6; maxDepth += 2) {
            int depthBestR = bestR, depthBestC = bestC;
            int depthBestScore = Integer.MIN_VALUE;
            boolean searchComplete = true;

            for (int i = 0; i < limit; i++) {
                if (System.nanoTime() > deadline) {
                    searchComplete = false;
                    break;
                }
                int r = candidates.get(i)[0], c = candidates.get(i)[1];

                board[r][c] = aiPiece;
                int score = minimax(board, 1, Integer.MIN_VALUE + 1,
                        Integer.MAX_VALUE - 1, false, deadline, maxDepth);
                board[r][c] = GameEngine.EMPTY;

                if (score > depthBestScore) {
                    depthBestScore = score;
                    depthBestR = r;
                    depthBestC = c;
                }
            }

            // 如果这一层搜完了，就保存为最佳结果
            if (searchComplete) {
                bestR = depthBestR;
                bestC = depthBestC;
                bestScore = depthBestScore;
            } else {
                // 超时了，用上一层的完整结果
                break;
            }
        }

        return new int[]{bestR, bestC};
    }

    /**
     * 检查是否有立即获胜或必须堵的位置
     * @return 立即落子的位置，或 null
     */
    private int[] findImmediateMove(int[][] board) {
        int[] win = findWinningMove(board, aiPiece);
        if (win != null) return win;
        int[] block = findWinningMove(board, humanPiece);
        if (block != null) return block;
        return null;
    }

    /**
     * Minimax 递归搜索 + Alpha-Beta 剪枝
     *
     * @param maxDepth 最大搜索深度（迭代加深外部控制）
     */
    private int minimax(int[][] board, int depth, int alpha, int beta, boolean isMin,
                        long deadline, int maxDepth) {
        if (System.nanoTime() > deadline) return evaluateBoard(board);
        if (depth >= maxDepth) return evaluateBoard(board);

        // 生成候选位置（深层更少候选，保持速度）
        java.util.ArrayList<int[]> moves = new java.util.ArrayList<>();
        int role = isMin ? humanPiece : aiPiece;
        int oppRole = isMin ? aiPiece : humanPiece;

        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] != GameEngine.EMPTY || !hasNeighbor(board, r, c, 2)) continue;
                // 用攻防综合评分排序
                int atk = evaluatePosition(board, r, c, role);
                int def = evaluatePosition(board, r, c, oppRole);
                moves.add(new int[]{r, c, atk + def * 2});
            }
        }
        java.util.Collections.sort(moves, new java.util.Comparator<int[]>() {
            public int compare(int[] a, int[] b) { return b[2] - a[2]; }
        });
        // 深度越浅候选越多，深度越深候选越少
        int maxCands = (depth <= 1) ? 15 : (depth <= 2) ? 10 : 6;
        int limit = Math.min(maxCands, moves.size());
        if (limit == 0) return evaluateBoard(board);

        if (isMin) {
            // 人类走棋：极小化
            int minScore = Integer.MAX_VALUE;
            for (int i = 0; i < limit; i++) {
                if (System.nanoTime() > deadline) return minScore;
                int r = moves.get(i)[0], c = moves.get(i)[1];
                board[r][c] = humanPiece;
                if (hasFive(board, r, c, humanPiece)) { board[r][c] = GameEngine.EMPTY; return -SCORE_FIVE + (maxDepth - depth) * 1000; }
                // 检测活四/双活三威胁
                int threat = countThreats(board, r, c, humanPiece);
                int score = minimax(board, depth + 1, alpha, beta, false, deadline, maxDepth);
                if (threat >= 2) score -= SCORE_LIVE_THREE * threat;
                board[r][c] = GameEngine.EMPTY;
                minScore = Math.min(minScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
            return minScore;
        } else {
            int maxScore = Integer.MIN_VALUE;
            for (int i = 0; i < limit; i++) {
                if (System.nanoTime() > deadline) return maxScore;
                int r = moves.get(i)[0], c = moves.get(i)[1];
                board[r][c] = aiPiece;
                if (hasFive(board, r, c, aiPiece)) { board[r][c] = GameEngine.EMPTY; return SCORE_FIVE - (maxDepth - depth) * 1000; }
                int threat = countThreats(board, r, c, aiPiece);
                int score = minimax(board, depth + 1, alpha, beta, true, deadline, maxDepth);
                if (threat >= 2) score += SCORE_LIVE_THREE * threat; // 能造双威胁是优势
                board[r][c] = GameEngine.EMPTY;
                maxScore = Math.max(maxScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            }
            return maxScore;
        }
    }

    /**
     * 对整个棋盘做静态评估（AI视角，正值=AI有利）
     *
     * 扫描所有行、列、对角线，统计每条线上的棋型，
     * 避免逐棋子累加导致的重复计数。
     */
    private int evaluateBoard(int[][] board) {
        int aiScore = 0, humanScore = 0;

        // 方向：水平(1,0)、垂直(0,1)、正斜(1,1)、反斜(1,-1)
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] == GameEngine.EMPTY) continue;
                // 在每个方向只统计一次（从每条线的起点开始）
                for (int[] d : GameEngine.DIRS) {
                    int pr = r - d[0], pc = c - d[1];
                    if (GameEngine.inside(pr, pc) && board[pr][pc] == board[r][c]) continue; // 不是起点

                    // 统计该方向上的连子
                    int cnt = 1, open = 0;
                    int nr = r + d[0], nc = c + d[1];
                    while (GameEngine.inside(nr, nc) && board[nr][nc] == board[r][c]) { cnt++; nr += d[0]; nc += d[1]; }
                    if (GameEngine.inside(nr, nc) && board[nr][nc] == GameEngine.EMPTY) open++;
                    int or = r - d[0], oc = c - d[1];
                    if (GameEngine.inside(or, oc) && board[or][oc] == GameEngine.EMPTY) open++;

                    if (cnt >= 5) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_FIVE;
                        else humanScore += SCORE_FIVE;
                    } else if (cnt == 4 && open == 2) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_LIVE_FOUR;
                        else humanScore += SCORE_LIVE_FOUR;
                    } else if (cnt == 4 && open == 1) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_HALF_FOUR;
                        else humanScore += SCORE_HALF_FOUR;
                    } else if (cnt == 3 && open == 2) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_LIVE_THREE;
                        else humanScore += SCORE_LIVE_THREE;
                    } else if (cnt == 3 && open == 1) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_HALF_THREE;
                        else humanScore += SCORE_HALF_THREE;
                    } else if (cnt == 2 && open == 2) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_LIVE_TWO;
                        else humanScore += SCORE_LIVE_TWO;
                    } else if (cnt == 2 && open == 1) {
                        if (board[r][c] == aiPiece) aiScore += SCORE_HALF_TWO;
                        else humanScore += SCORE_HALF_TWO;
                    }
                }
            }
        }
        return aiScore - humanScore;
    }

    private int[] findEasyMove(int[][] board, int moveCount) {
        if (RANDOM.nextInt(10) < 7) return findRandomMove(board, moveCount);
        int[] win = findWinningMove(board, aiPiece);
        if (win != null) return win;

        int bestR = -1, bestC = -1, bestScore = Integer.MIN_VALUE;
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] != GameEngine.EMPTY || !hasNeighbor(board, r, c, 1)) continue;
                int atk = evaluatePosition(board, r, c, aiPiece);
                int score = atk / 4 + 6 - (Math.abs(r - 7) + Math.abs(c - 7)) + RANDOM.nextInt(200);
                if (score > bestScore) {
                    bestScore = score;
                    bestR = r;
                    bestC = c;
                }
            }
        }
        return bestR == -1 ? findRandomMove(board, moveCount) : new int[]{bestR, bestC};
    }

    private int[] findWinningMove(int[][] board, int role) {
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                if (board[r][c] != GameEngine.EMPTY) continue;
                if (wouldWin(board, r, c, role)) return new int[]{r, c};
            }
        }
        return null;
    }

    private boolean wouldWin(int[][] board, int row, int col, int role) {
        board[row][col] = role;
        boolean win = hasFive(board, row, col, role);
        board[row][col] = GameEngine.EMPTY;
        return win;
    }

    private int[] findRandomMove(int[][] board, int moveCount) {
        int total = 0;
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++)
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++)
                if (board[r][c] == GameEngine.EMPTY && (moveCount == 0 || hasNeighbor(board, r, c, 2))) total++;
        if (total <= 0) return GameEngine.CENTER.clone();
        int target = RANDOM.nextInt(total), idx = 0;
        for (int r = 0; r < GameEngine.BOARD_SIZE; r++)
            for (int c = 0; c < GameEngine.BOARD_SIZE; c++)
                if (board[r][c] == GameEngine.EMPTY && (moveCount == 0 || hasNeighbor(board, r, c, 2))) {
                    if (idx == target) return new int[]{r, c};
                    idx++;
                }
        return GameEngine.CENTER.clone();
    }

    private int evaluatePosition(int[][] board, int row, int col, int role) {
        return scoreDirection(board, row, col, role, 1, 0)
             + scoreDirection(board, row, col, role, 0, 1)
             + scoreDirection(board, row, col, role, 1, 1)
             + scoreDirection(board, row, col, role, 1, -1);
    }

    private int countThreats(int[][] board, int row, int col, int role) {
        return (scoreDirection(board, row, col, role, 1, 0) >= SCORE_LIVE_THREE ? 1 : 0)
             + (scoreDirection(board, row, col, role, 0, 1) >= SCORE_LIVE_THREE ? 1 : 0)
             + (scoreDirection(board, row, col, role, 1, 1) >= SCORE_LIVE_THREE ? 1 : 0)
             + (scoreDirection(board, row, col, role, 1, -1) >= SCORE_LIVE_THREE ? 1 : 0);
    }

    private int scoreDirection(int[][] board, int row, int col, int role, int dr, int dc) {
        int cnt = 1, open = 0;
        int r = row + dr, c = col + dc;
        while (GameEngine.inside(r, c) && board[r][c] == role) { cnt++; r += dr; c += dc; }
        if (GameEngine.inside(r, c) && board[r][c] == GameEngine.EMPTY) open++;
        r = row - dr; c = col - dc;
        while (GameEngine.inside(r, c) && board[r][c] == role) { cnt++; r -= dr; c -= dc; }
        if (GameEngine.inside(r, c) && board[r][c] == GameEngine.EMPTY) open++;
        if (cnt >= 5) return SCORE_FIVE;
        if (cnt == 4 && open == 2) return SCORE_LIVE_FOUR;
        if (cnt == 4 && open == 1) return SCORE_HALF_FOUR;
        if (cnt == 3 && open == 2) return SCORE_LIVE_THREE;
        if (cnt == 3 && open == 1) return SCORE_HALF_THREE;
        if (cnt == 2 && open == 2) return SCORE_LIVE_TWO;
        if (cnt == 2 && open == 1) return SCORE_HALF_TWO;
        if (cnt == 1 && open == 2) return SCORE_LIVE_ONE;
        return SCORE_DEAD;
    }

    private boolean hasFive(int[][] board, int row, int col, int role) {
        return countDirection(board, row, col, role, 1, 0) >= GameEngine.WIN_COUNT
            || countDirection(board, row, col, role, 0, 1) >= GameEngine.WIN_COUNT
            || countDirection(board, row, col, role, 1, 1) >= GameEngine.WIN_COUNT
            || countDirection(board, row, col, role, 1, -1) >= GameEngine.WIN_COUNT;
    }

    private int countDirection(int[][] board, int row, int col, int role, int dr, int dc) {
        int cnt = 1;
        int r = row + dr, c = col + dc;
        while (GameEngine.inside(r, c) && board[r][c] == role) { cnt++; r += dr; c += dc; }
        r = row - dr; c = col - dc;
        while (GameEngine.inside(r, c) && board[r][c] == role) { cnt++; r -= dr; c -= dc; }
        return cnt;
    }

    private boolean hasNeighbor(int[][] board, int row, int col, int distance) {
        for (int r = row - distance; r <= row + distance; r++)
            for (int c = col - distance; c <= col + distance; c++)
                if (GameEngine.inside(r, c) && board[r][c] != GameEngine.EMPTY) return true;
        return false;
    }
}
