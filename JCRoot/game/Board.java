package JCRoot.game;

import java.util.LinkedList;
import java.util.Scanner;

public class Board {
    public char[] alpha = new char[]{'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
    public char[] charset = new char[]{'#', '-', '+', '#', 'N', 'N'};
    public final int w, h, p;
    public Cell[][] board;
    public Color gray = new Color(-1);
    public Board(int w, int h, int p) {
        this.w = w;
        this.h = h;
        this.p = p;
        board = new Cell[h][w];
        for (int y = 0; y < h; y ++) {
            for (int x = 0; x < w; x ++) {
                board[y][x] = new Cell(x, y, 1, -1);
            }
        }
    }
    public boolean willTopple(int x, int y) {
        boolean xt = (x == 0 || x == (w-1));
        boolean yt = (y == 0 || y == (h-1));
        return (board[y][x].value >= (4-(xt?1:0)-(yt?1:0)));
    }
    private void getNeightbors(int x, int y, LinkedList<Cell> cells) {
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
                    return -1;
                }
            }
        }
        return cteam;
    }
    public void topple(int x, int y, int team) {
        LinkedList<Cell> toTopple = new LinkedList<>();
        board[y][x].value = 1;
        getNeightbors(x, y, toTopple);
        while (!toTopple.isEmpty()) {
            Cell c = toTopple.removeLast();
            c.team = team;
            if (willTopple(c.x, c.y)) {
                c.value = 1;
                getNeightbors(c.x, c.y, toTopple);
            } else {
                c.value ++;
            }
            int winner = checkWinner();
            if (winner >= 0) {
                return;
            }
        }
    }
    public void addTo(int x, int y, int team) {
        // System.out.println("ADDITION: " + x + ", " + y + " (" + team + ")");
        board[y][x].team = team;
        if (willTopple(x, y)) {
            topple(x, y, team);
        } else {
            board[y][x].value ++;
        }
    }
    public String toString() {
        String f = "    ";
        for (int i = 0; i < w; i ++) {
            f += String.format(" %s ", alpha[i]);
        }
        f += "\n\n";
        for (int y = 0; y < h; y ++) {
            f += String.format(" % 2d ", (y+1));
            for (int x = 0; x < w; x ++) {
                f += String.format(" %s%s%s ", new Color(board[y][x].team), charset[board[y][x].value], gray);
            }
            f += Color.DEFAULT;
            f += "\n\n";
        }
        return f.substring(0, f.length()-2);
    }
    public static void main(String[] args) {
        Board b = new Board(5, 5, 3);
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