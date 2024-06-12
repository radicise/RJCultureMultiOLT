package JCRoot;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import JCRoot.game.*;
import JCRoot.menu.*;

public class Client {
    private static int pnum = -1;
    private static TreeMap<Integer, Player> players = new TreeMap<>();
    private static Scanner sc = new Scanner(System.in);
    private static Board board;
    private static int sid;
    private static Socket sock, sock2;
    private static InputStream sIn, s2In;
    private static OutputStream sOut, s2Out;
    private static int gamestate = 0;
    private static boolean exiting = false, ready = true;
    private static int needInput = 0;
    private static LinkedBlockingQueue<String> inputs = new LinkedBlockingQueue<>();
    private static final Object EXIT_LOCK = new Object(), READY_LOCK = new Object();
    private static CountDownLatch countdown = null;
    private static int[] protver = new int[3];
    private static boolean isPVer(int maj, int seg, int min) {
        return ((maj == (-1)) || (protver[0] == maj)) && ((seg == (-1)) || (protver[1] == seg)) && ((min == (-1)) || protver[2] == min);
    }
    private static int read(InputStream in) throws Exception {
        int r = in.read();
        if (r < 0) crash();
        return r;
    }
    private static int read(InputStream in, byte[] buf) throws Exception {
        if (in.read(buf) != buf.length) crash();
        return buf.length;
    }
    private static boolean confirmJoin(InetAddress addr, int port) {
        try (Socket sock = new Socket(addr, port)) {
            OutputStream sOut = sock.getOutputStream();
            InputStream sIn = sock.getInputStream();
            sOut.write(0x66);
            boolean hasPass = read(sIn) > 0;
            byte[] buf = new byte[read(sIn)];
            read(sIn, buf);
	    byte[] vbuf = new byte[6];
	    //read(sIn, vbuf);
	    vbuf[0] = (byte) (read(sIn));
	    vbuf[1] = (byte) (read(sIn));
	    vbuf[2] = (byte) (read(sIn));
	    vbuf[3] = (byte) (read(sIn));
	    vbuf[4] = (byte) (read(sIn));
	    vbuf[5] = (byte) (read(sIn));
	    protver[0] = ((vbuf[0] & 0xff) << 8) | (vbuf[1] & 0xff);
	    protver[1] = ((vbuf[2] & 0xff) << 8) | (vbuf[3] & 0xff);
	    protver[2] = ((vbuf[4] & 0xff) << 8) | (vbuf[5] & 0xff);
	    //sOut.write(0x00);
	    //Thread.sleep(500);
            //sock.close();
            String hname = new String(buf);
            System.out.printf("Confirm joining \"%s\" with%s password? (Y/n) ", hname, hasPass?"":"out");
            if (sc.nextLine().toLowerCase().matches("(n|no)")) {
                return false;
            }
        } catch (Exception E) {
            E.printStackTrace();
        }
        return true;
    }
    private static boolean passwordCheck() throws Exception {
        OutputStream sOut = sock.getOutputStream();
        InputStream sIn = sock.getInputStream();
        while (true) {
            System.out.print("Enter password: ");
            String pass = sc.nextLine();
            sOut.write(pass.length());
            sOut.write(pass.getBytes());
            if (read(sIn) == 0) {
                System.out.println("INCORRECT PASSWORD");
                System.out.print("Try again? (Y/n) ");
                if (sc.nextLine().toLowerCase().matches("(n|no)")) {sOut.write(0);return false;}
                sOut.write(1);
                continue;
            }
            System.out.println("PASSWORD ACCEPTED");
            return true;
        }
    }
    private static void options() throws Exception {
        MENU.setState(Menu.TOP);
        while (true) {
            MInputData mid = MENU.run(sc);
            if (mid.isNULL()) {
                continue;
            }
            if (mid.isEXIT()) {
                return;
            }
            ItemData itemd = (ItemData)mid.data;
            if (itemd.iid == BOARD_COMPACT) {
                Board.compact = itemd.getToggleState();
            } else if (itemd.iid == BOARD_BRGHT_VOLATILE) {
                Board.brightenVolatiles = itemd.getToggleState();
            } else if (itemd.iid == BOARD_HIGHLIGHT_MOVE) {
                Board.highlightMoves = itemd.getToggleState();
            }
        }
    }
    private static void cli() throws Exception {
        while (true) {
            String inp = sc.nextLine();
            if (needInput > 0) {
                needInput --;
                inputs.add(inp);
                continue;
            }
            if (gamestate == 0) {
                if (inp.equalsIgnoreCase("exit")) {
                    s2Out.write(0);
                    synchronized(EXIT_LOCK) {
                        exiting = true;
                    }
                    try {read(sIn);}
                    catch(ClientGoneException CGE) {}
                    Player p = players.get(pnum);
                    System.out.printf("you (\"%s\") have left %s%s%s%n", p.name, p.team.color, p.team.name, Color.DEFAULT);
                    sock.close();
                    sock2.close();
                    System.exit(0);
                }
                if (inp.equalsIgnoreCase("options")) {
                    synchronized(READY_LOCK) {
                        if (gamestate != 0) continue;
                        ready = false;
                        countdown = new CountDownLatch(1);
                    }
                    options();
                    ready = true;
                    countdown.countDown();
                }
            }
        }
    }
    private static void s2handshake(InetAddress addr, int port) {
        try {
            sock2 = new Socket(addr, port);
            s2In = sock2.getInputStream();
            s2Out = sock2.getOutputStream();
            s2Out.write(0x22);
            s2Out.write(sid>>8);
            s2Out.write(sid&0xff);
            if (read(s2In) == 1) {
                return;
            }
            throw new IllegalStateException("S2HANDSHAKE FAILURE");
        } catch (Exception E) {
            throw new IllegalStateException("S2 CONNECT FAILURE");
        }
    }
    private static void start(InetAddress addr, int port) {
        if (!confirmJoin(addr, port)) return;
        try (Socket ssock = new Socket(addr, port)) {
            Client.sock = ssock;
            sIn = sock.getInputStream();
            sOut = sock.getOutputStream();
            sOut.write(0x44);
            if (read(sIn) == 2) {System.out.println("HOST NOT CURRENTLY ACCEPTING PLAYERS");sock.close();return;}
            System.out.println("CONNECTED");
            sid = (read(sIn)<<8) | read(sIn);
            s2handshake(addr, port);
            System.out.println("S2 CONNECTED");
            if (read(sIn) > 0) {
                if (!passwordCheck()) {
                    sock.close();
                    return;
                }
            }
            System.out.print("Enter nickname: ");
            String clname = sc.nextLine();
            sOut.write(clname.length());
            sOut.write(clname.getBytes());
            pnum = read(sIn);
            int teamid = read(sIn);
            // Team team = new Team(teamid, new Color(teamid), clname);
            // Teams.teams.put(teamid, team);
            Player p = new Player(pnum, Teams.teams[teamid], clname);
            players.put(pnum, p);
            System.out.printf("you (\"%s\") have joined %s%s%s%n", p.name, p.team.color, p.team.name, Color.DEFAULT);
            preloop();
        } catch (Exception E) {
            E.printStackTrace();
        }
    }
    private static void preloop() throws Exception {
        {
            Thread t = new Thread(){public void run(){try{cli();}catch(Exception E){E.printStackTrace();}}};
            t.setDaemon(true);
            t.start();
        }
        while (true) {
            int commcode;
            try {commcode=read(sIn);}
            catch (ClientGoneException CGE) {return;}
            if (commcode == 0) {
                System.out.println("THE HOST ENDED THE SESSION");
                sock.close();
                sock2.close();
                return;
            }
            if (commcode == 1) {
                System.out.println("HOST HAS STARTED THE GAME");
                synchronized(READY_LOCK) {
                    if (ready) {
                        gamestate = 1;
                    }
                }
                if (gamestate != 1) {
                    countdown.await();
                }
                gamestate = 1;
                sOut.write(1);
                Teams.reset();
                board = new Board(read(sIn), read(sIn));
                if (isPVer(0, 0, 1)) {
			read(sIn);
		}
		gameloop();
            }
            if (commcode == 2) {
                byte[] b = new byte[read(sIn)];
                read(sIn, b);
                String otname = new String(b);
                int pid = read(sIn);
                int tid = read(sIn);
                // Team team = new Team(tid, new Color(tid), otname);
                // Teams.teams.put(tid, team);
                Team team = Teams.teams[tid];
                players.put(pid, new Player(pid, team, otname));
                System.out.printf("\"%s\" has joined %s%s%s%n", otname, team.color, team.name, Color.DEFAULT);
            }
            if (commcode == 3) {
                int pid = read(sIn);
                int tid = read(sIn);
                Player p = players.get(pid);
                Team nteam = Teams.teams[tid];
                System.out.printf("\"%s\" has switched from team %s%s%s to team %s%s%s%n", p.name, p.team.color, p.team.name, Color.DEFAULT, nteam.color, nteam.name, Color.DEFAULT);
                p.team = nteam;
            }
            if (commcode == 4) {
                int pid = read(sIn);
                Player left = players.remove(pid);
                System.out.printf("\"%s\" from %s%s%s has left%n", left.name, left.team.color, left.team.name, Color.DEFAULT);
            }
            if (commcode == 5) {
                int setid = read(sIn);
                Board.CHARI = setid;
            }
            if (commcode == 6) {
                int setcount = read(sIn);
                Board.CHARI = 0;
                Board.tilesets.clear();
                for (int i = 0; i < setcount; i ++) {
                    char[] set = new char[]{'!', '-', '-', '-', '-', 'N'};
                    for (int j = 1; j < 5; j ++) {
                        set[j] = (char)((read(sIn)<<8)|read(sIn));
                    }
                    Board.tilesets.add(set);
                }
            }
        }
    }
    private static void gameloop() throws Exception {
        while (true) {
            System.out.println(board);
            if (board.checkWinner() != -2) {
                // System.out.printf("Team %s%s has won!\n", Teams.teams[board.checkWinner()], Color.DEFAULT);
                System.out.printf("Team %s has won!%n", Teams.teams[board.checkWinner()]);
                Board.scoreboard();
                gamestate = 0;
                return;
            }
            int ccode = read(sIn);
            if (ccode == 1) {
                int row;
                int col;
                while (true) {
                    System.out.printf("%sEnter Move:%s%n", players.get(pnum).team.color, Color.DEFAULT);
                    needInput ++;
                    String l = inputs.take().toUpperCase();
                    if (l.length() == 0) {
                        System.out.println("[Malformed input]");
                        continue;
                    }
                    col = ((int)l.charAt(0)) - ((int)'A');
                    if (col < 0 || col >= board.w) {
                        System.out.println("[Malformed input]");
                        continue;
                    }
                    if (l.substring(1).matches("^[0-9]{1,2}$")) {
                        row = Integer.parseInt(l.substring(1))-1;
                    } else {
                        System.out.println("[Malformed input]");
                        continue;
                    }
                    if (row < 0 || row >= board.h) {
                        System.out.println("[Malformed input]");
                        continue;
                    }
                    sOut.write(col);
                    sOut.write(row);
                    if (read(sIn) == 0) {
                        System.out.println("Invalid move");
                        continue;
                    }
                    break;
                }
            }
            int col = read(sIn);
            int row = read(sIn);
            int team = read(sIn);
            if (ccode != 1) {
                System.out.printf("%n%s%c%d%s%n", Teams.teams[team].color, board.alpha[col], (row+1), Color.DEFAULT);
            }
            board.addTo(col, row, team);
        }
    }
    private static void crash() throws Exception {
        synchronized(EXIT_LOCK) {
            if (exiting) throw new ClientGoneException();
        }
        System.out.println();
        System.out.println("GAME CRASHED");
        System.out.println();
        throw new IllegalStateException();
    }
    public static void main(String[] args) throws Exception {
        Color.start();
        start(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
    }
    private static final Menu MENU;
    private static final int
    BOARD_COMPACT = 0,
    BOARD_BRGHT_VOLATILE = 1,
    BOARD_HIGHLIGHT_MOVE = 2;
    static {
        MenuFrame top = new MenuFrame("Client Options");
        {
            MenuFrame board = new MenuFrame("Board Options");
            board.setAcceptNumbers(true);
            board.addItem("compact mode", ItemData.Toggle(false).withIID(BOARD_COMPACT));
            board.addItem("brighten volatiles", ItemData.Toggle(true).withIID(BOARD_BRGHT_VOLATILE));
            board.addItem("highlight last move", ItemData.Toggle(true).withIID(BOARD_HIGHLIGHT_MOVE));
            top.addItem("board", ItemData.Group(board));
        }
        MENU = new Menu(top);
    }
}
