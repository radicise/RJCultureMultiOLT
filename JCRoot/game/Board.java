package JCRoot.game;

import java.util.LinkedList;
import java.util.Scanner;

public class Board {
    public static LinkedList<char[]> tilesets = new LinkedList<>();
    public static int CHARI = 0;
    public static boolean compact = false, brightenVolatiles = true, highlightMoves = true;
    static {
        tilesets.add(new char[]{'!', '-', '+', '#', 'N', 'N'});
        tilesets.add(new char[]{'!', '-', '+', 'Y', 'X', 'N'});
    }
    public char[] alpha = new char[]{'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
    public char[] charset = tilesets.get(Board.CHARI);
    public int chari = Board.CHARI;
    public final int w, h;
    public Cell[][] board;
    private Cell lastMove = null;
    public Board(int w, int h) {
        this.w = w;
        this.h = h;
        board = new Cell[h][w];
        for (int y = 0; y < h; y ++) {
            for (int x = 0; x < w; x ++) {
                board[y][x] = new Cell(x, y, 1, -1);
            }
        }
    }
    public boolean willTopple(int x, int y) {
        return board[y][x].value >= (4-(x==0?1:0)-(x==(w-1)?1:0)-(y==0?1:0)-(y==(h-1)?1:0));
        // boolean xt = (x == 0 || x == (w-1));
        // boolean yt = (y == 0 || y == (h-1));
        // return (board[y][x].value >= (4-(xt?1:0)-(yt?1:0)));
    }
    private void getNeighbors(int x, int y, LinkedList<Cell> cells) {
        if (x > 0) {
            cells.add(board[y][x-1]);
        }
        if (x < (w-1)) {
            cells.add(board[y][x+1]);
        }
        if (y > 0) {
            cells.add(board[y-1][x]);
        }
        if (y < (h-1)) {
            cells.add(board[y+1][x]);
        }
    }
    public int checkWinner() {
        int cteam = board[0][0].team;
        for (int y = 0; y < h; y ++) {
            for (int x = 0; x < w; x ++) {
                if (board[y][x].team != cteam) {
                    return -2;
                }
            }
        }
        if (cteam == -1) {
            return -2;
        }
        return cteam;
    }
    public void topple(int x, int y, int team) {
        LinkedList<Cell> toTopple = new LinkedList<>();
        board[y][x].value = 1;
        getNeighbors(x, y, toTopple);
        while (!toTopple.isEmpty()) {
            Cell c = toTopple.removeLast();
            if (c.team != team) {
                Teams.teams[team].tscore += c.value;
            }
            c.team = team;
            if (willTopple(c.x, c.y)) {
                c.value = 1;
                getNeighbors(c.x, c.y, toTopple);
            } else {
                c.value ++;
            }
            int winner = checkWinner();
            if (winner > -2) {
                return;
            }
        }
    }
    public void addTo(int x, int y, int team) {
        // System.out.println("ADDITION: " + x + ", " + y + " (" + team + ")");
        lastMove = board[y][x];
        if (board[y][x].team != team) {
            Teams.teams[team].tscore ++;
        }
        board[y][x].team = team;
        if (willTopple(x, y)) {
            topple(x, y, team);
        } else {
            board[y][x].value ++;
        }
    }
    private String getColor(int x, int y) {
        String hl = (Board.highlightMoves && lastMove != null) ? ((x==lastMove.x&&y==lastMove.y)?Color.HIGHLIGHT:"") : "";
        if (Board.brightenVolatiles && willTopple(x, y)) {
            return Color.VOLATILE_COLORS[board[y][x].team+1]+Color.HC_BACKGROUND+hl;
        }
        // return Color.VOLATILE_COLORS[board[y][x].team+1];
        return Color.NORMAL_COLORS[board[y][x].team+1]+hl;
    }
    public String toStringCompact() {
        String f = "   ";
        for (int i = 0; i < w; i ++) {
            f += String.format("%c ", alpha[i]);
        }
        f += "\n";
        for (int y = 0; y < h; y ++) {
            f += String.format("%2d ", (y+1));
            for (int x = 0; x < w; x ++) {
                f += String.format("%s%c%s ", getColor(x, y), charset[board[y][x].value], Color.GRAY);
            }
            f += Color.DEFAULT;
            f += String.format("%d", (y+1));
            f += '\n';
        }
        f += "   ";
        for (int i = 0; i < w; i ++) {
            f += String.format("%c ", alpha[i]);
        }
        return f;
    }
    public String toString() {
        if (Board.compact) return toStringCompact();
        String f = "    ";
        for (int i = 0; i < w; i ++) {
            f += String.format("  %c ", alpha[i]);
        }
        f += "\n\n";
        for (int y = 0; y < h; y ++) {
            f += String.format(" %2d  ", (y+1));
            for (int x = 0; x < w; x ++) {
                f += String.format(" %s%c%s  ", getColor(x, y), charset[board[y][x].value], Color.GRAY);
            }
            f += Color.DEFAULT;
            f += String.format(" %d ", (y+1));
            f += "\n\n";
        }
        f += "    ";
        for (int i = 0; i < w; i ++) {
            f += String.format("  %s ", alpha[i]);
        }
        return f;
    }
    public static void scoreboard() {
        System.out.printf("%sScore Board:\n", Color.DEFAULT);
        Team[] steams = new Team[6];
        for (int i = 0; i < 6; i ++) steams[i] = Teams.teams[i];
        for (int i = 0; i < 6; i ++) {
            int hscore = 0, hindex = i;
            for (int j = i; j < 6; j ++) {
                Team t = steams[j];
                if (t.tscore > hscore) {
                    hscore = t.tscore;
                    hindex = j;
                }
            }
            Team h = steams[i];
            steams[i] = steams[hindex];
            steams[hindex] = h;
        }
        for (int i = 0; i < 6; i ++) {
            Team t = steams[i];
            System.out.printf("#%d %s -- %d\n", (i+1), t, t.tscore);
        }
    }
    public static void main(String[] args) {
        Color.start();
        int team = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        compact = true;
        Board b = new Board(3, 3);
        b.addTo(0, 0, team);
        b.addTo(0, 0, team);
        b.addTo(0, 0, team);
        b.addTo(0, 0, team);
        b.addTo(1, 1, team);
        b.addTo(1, 1, team);
        Scanner sc = new Scanner(System.in);
        while (b.checkWinner() == -1) {
            System.out.println(b);
            String inp = sc.nextLine();
            b.addTo(Integer.parseInt(inp.substring(0, inp.indexOf(','))), Integer.parseInt(inp.substring(inp.indexOf(',')+1)), 0);
        }
        System.out.println(b);
        sc.close();
    }
}
