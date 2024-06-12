package JCRoot.game;

import java.util.ArrayList;
import java.util.stream.Collectors;

import JCRoot.Host;

public class Game {
    public final int width, height;
    public final Board board;
    public ArrayList<Integer> plist;
    // public int[] plist = Host.players.navigableKeySet().stream().mapToInt(e->e).toArray();
    public int pindex = 0;
    // public int cplayer = plist[pindex];
    public int cplayer;
    public Game(int w, int h) {
        synchronized(Host.SPEC_LOCK) {
            plist = new ArrayList<>(Host.players.navigableKeySet().stream().filter(e->!Host.spectators.contains(e)).collect(Collectors.toList()));
        }
        cplayer = plist.get(pindex);
        width = w;
        height = h;
        board = new Board(w, h);
    }
    public boolean canMove(int team) {
        for (int y = 0; y < height; y ++) {
            for (int x = 0; x < width; x ++) {
                if (board.board[y][x].team == -1 || board.board[y][x].team == team) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean validate(int x, int y, int team) {
        if (x < 0 || y < 0 || x >= width || y >= height || team < 0 || team >= 6) {
            return false;
        }
        return board.board[y][x].team == team || board.board[y][x].team == -1;
    }
    public void move(int x, int y) {
        board.addTo(x, y, Host.players.get(cplayer).team.id);
        if (board.checkWinner() < 0) {
            do {
                pindex ++;
                // pindex = pindex % plist.length;
                // cplayer = plist[pindex];
                pindex = pindex % plist.size();
                cplayer = plist.get(pindex);
            } while (!canMove(Host.players.get(cplayer).team.id));
        }
    }
}
