package JCRoot;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe.SinkChannel;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;


import JCRoot.game.*;
import JCRoot.menu.ItemData;
import JCRoot.menu.MInputData;
import JCRoot.menu.Menu;
import JCRoot.menu.MenuFrame;

class Util {
	public static final String NL = System.lineSeparator();
}
public class Host {
    private static volatile int itcw=0, itch=0, itcp=0;
    private static boolean usingPass = false;
    private static String hostname = "";
    private static String hostpass = "";
    private static Scanner sc = new Scanner(System.in);
    public static TreeMap<Integer, Player> players = new TreeMap<>();
    public static TreeMap<Integer, Connection> connections = new TreeMap<>();
    public static TreeSet<Integer> spectators = new TreeSet<>();
    private static Game game;
    private static LinkedBlockingDeque<Integer> comms = new LinkedBlockingDeque<>();
    private static Thread servingThread = null;
    private static CountDownLatch countdown = null;
    private static final Object SID_LOCK = new Object();
    public static final Object SPEC_LOCK = new Object();
    private static volatile int CSID = 0;
    private static volatile int gamestate = 0;
    public static final int VER_MAJOR = 0;
    public static final int VER_MINOR = 1;
    public static final int VER_INFRAMINOR = 1;
    public static final Path Pathof(String s) {
	return FileSystems.getDefault().getPath(s);
    }
    private static int getRestrictNum(String prompt, int lo, int hi) {
        while (true) {
            System.out.print(prompt);
            try {
                String l = sc.nextLine();
                if (l.equalsIgnoreCase("cancel") || l.equalsIgnoreCase("done")) return lo-1;
                int i = Integer.parseInt(l);
                if (i >= lo && i <= hi) {
                    return i;
                }
            } catch (Exception E) {}
            System.out.println("invalid");
        }
    }
    private static void backrunner() throws Exception {
        while (true) {
            int pcode = comms.takeFirst();
            Player p = players.get(pcode);
            p.pipe2.sink().write(ByteBuffer.wrap(new byte[]{0}));
            byte[] codebuf = new byte[1];
            p.pipe2.source().read(ByteBuffer.wrap(codebuf));
            if (gamestate == 0) {
                if (codebuf[0] == 0) {
                    System.out.printf("\"%s\" from %s%s%s has left%n", p.name, p.team.color, p.team.name, Color.DEFAULT);
                    synchronized(players) {
                        players.remove(pcode);
                        p.conn.s2O.write(0);
                        p.conn.s2O.flush();
                        p.conn.close(true);
                        countdown = new CountDownLatch(players.size());
                        for (Player player : players.values()) {
                            player.pipe.sink().write(ByteBuffer.wrap(new byte[]{4, (byte)(p.id)}));
                        }
                        countdown.await();
                    }
                }
            }
        }
    }
    private static void handle2(Player player) throws Exception {
        InputStream s2I = player.conn.s2I;
        while (true) {
            int dcode;
            try {dcode=read(player.conn, s2I);}
            catch (ClientGoneException CGE) {
                return;
            }
            if (dcode == 0) {
                comms.add(player.id);
                player.pipe2.source().read(ByteBuffer.wrap(new byte[1]));
                player.pipe2.sink().write(ByteBuffer.wrap(new byte[]{0}));
                return;
            }
        }
    }
    private static void runloop() throws Exception {
        while (true) {
            for (Player p : players.values()) {
                // SinkChannel snk = p.pipe.sink();
                OutputStream pOut = p.conn.s1O;
                if (p.id == game.cplayer) {
                    pOut.write(1);
                    // snk.write(ByteBuffer.wrap(new byte[]{1}));
                } else {
                    pOut.write(0);
                    // snk.write(ByteBuffer.wrap(new byte[]{0}));
                }
            }
            Player player = players.get(game.cplayer);
            InputStream pIn = player.conn.s1I;
            OutputStream pOut = player.conn.s1O;
            int x, y;
            while (true) {
                x = read(pIn);
                y = read(pIn);
                if (game.validate(x, y, player.team.id)) {// TODO Fix disallowance bug
                    pOut.write(1);
                    break;
                } else {
                    //System.out.println(player.team.id);
                    //System.out.println(game.board.board[y][x].team);
                    pOut.write(0);
                }
            }
            // buf2[0] = (byte)x;
            // buf2[1] = (byte)y;
            // buf2[2] = (byte)player.team.id;
            // snk.write(ByteBuffer.wrap(buf2));
            // SourceChannel psrc = players.get(game.cplayer).pipe.source();
            // byte[] buf2 = new byte[3];
            // psrc.read(ByteBuffer.wrap(buf2));
            // game.move(buf2[0], buf2[1]);
            game.move(x, y);
            // System.out.println("RUNREAD:");
            // System.out.println(buf2);
            for (Player p : players.values()) {
                // SinkChannel snk = p.pipe.sink();
                // snk.write(ByteBuffer.wrap(new byte[]{2}));
                // snk.write(ByteBuffer.wrap(buf2));
                OutputStream sOut = p.conn.s1O;
                sOut.write(x);
                sOut.write(y);
                sOut.write(player.team.id);
            }
            if (game.board.checkWinner() != -2) {
                for (Player p : players.values()) {
                    SinkChannel snk = p.pipe.sink();
                    snk.write(ByteBuffer.wrap(new byte[]{3}));
                }
                // System.out.printf("Team %s%s won!\n", Teams.teams[game.board.checkWinner()], Color.DEFAULT);
                System.out.printf("Team %s won!%n", Teams.teams[game.board.checkWinner()]);
                Board.scoreboard();
                return;
            }
        }
    }
    private static Board getTestBoard() {
        Board testBoard = new Board(4, 1);
        testBoard.board[0][1].value = 2;
        testBoard.board[0][2].value = 3;
        testBoard.board[0][3].value = 4;
        return testBoard;
    }
    private static void pickTileset() throws Exception {
        Board testBoard = getTestBoard();
        while (true) {
            System.out.println(testBoard);
            System.out.printf("board %d/%d [%sprev%s/%snext%s/%sconfirm%s]?%n", testBoard.chari+1, Board.tilesets.size(), testBoard.chari > 0 ? Color.WHITE : Color.GRAY, Color.DEFAULT, testBoard.chari < (Board.tilesets.size()-1) ? Color.WHITE : Color.GRAY, Color.DEFAULT, Color.WHITE, Color.DEFAULT);
            String l = sc.nextLine();
            if (l.equalsIgnoreCase("cancel")) {
                return;
            } else if (l.equalsIgnoreCase("prev") && testBoard.chari > 0) {
                testBoard.chari --;
                testBoard.charset = Board.tilesets.get(testBoard.chari);
            } else if (l.equalsIgnoreCase("next") && testBoard.chari < (Board.tilesets.size()-1)) {
                testBoard.chari ++;
                testBoard.charset = Board.tilesets.get(testBoard.chari);
            } else if (l.equalsIgnoreCase("confirm")) {
                Board.CHARI = testBoard.chari;
                countdown = new CountDownLatch(players.size());
                for (Player player : players.values()) {
                    player.pipe.sink().write(ByteBuffer.wrap(new byte[]{5, (byte)Board.CHARI}));
                }
                countdown.await();
                return;
            } else {
                System.out.println("invalid");
            }
        }
    }
    private static void loadTilesets() throws Exception {
        String location = "tilesets.txt";
        System.out.print("Use default tilesets? (Y/n) ");
        if (sc.nextLine().toLowerCase().matches("(n|no)")) {
            System.out.print("Use default save location? (Y/n) ");
            if (sc.nextLine().toLowerCase().matches("(n|no)")) {
                while (true) {
                    System.out.print("Enter file location: ");
                    String l = sc.nextLine();
                    if (l.equalsIgnoreCase("cancel")) {
                        System.out.println("reverting to default location");
                        break;
                    }
                    if (Files.exists(Pathof(l))) {
                        location = l;
                        break;
                    }
                    System.out.println("couldn't find that file");
                }
            } else {
                location = "usersets.tset";
            }
        }
        if (!Files.exists(Pathof(location))) {
            System.out.println("couldn't find file");
            return;
        }
        System.out.printf("About to load tilesets from \"%s\"%nConfirm loading? (this will remove any other tilesets that are currently loaded) (y/N) ", location);
        if (!sc.nextLine().toLowerCase().matches("(y|yes)")) {
            System.out.println("aborting");
            return;
        }
        Stream<String> datastream = Files.lines(Pathof(location), StandardCharsets.UTF_8);
        String[] lines = datastream.toArray(String[]::new);
        datastream.close();
        Board.CHARI = 0;
        Board.tilesets.clear();
        for (String line : lines) {
            if (line.length() < 4) continue;
            char[] set = new char[]{'!','-','-','-','-','N'};
            for (int i = 1; i < 5; i ++) {
                set[i] = line.charAt(i-1);
            }
            Board.tilesets.add(set);
        }
        countdown = new CountDownLatch(players.size());
        for (Player player : players.values()) {
            player.pipe.sink().write(ByteBuffer.wrap(new byte[]{6}));
        }
        countdown.await();
    }
    private static void makeTileset() throws Exception {
        Board testBoard = getTestBoard();
        char[] set = new char[]{'!','-','-','-','-','N'};
        testBoard.charset = set;
        System.out.println(testBoard);
        while (true) {
            System.out.println("Enter characters in order, leave the line blank to use the current character");
            for (int i = 1; i < 5; i ++) {
                System.out.printf("Enter character %d: ", i);
                String l = sc.nextLine();
                if (l.length() == 0) continue;
                set[i] = l.charAt(0);
            }
            System.out.println(testBoard);
            System.out.print("Confirm? (y/N) ");
            String l = sc.nextLine();
            if (l.equalsIgnoreCase("cancel")) {
                return;
            }
            if (l.toLowerCase().matches("(y|yes)")) {
                break;
            }
        }
        Board.CHARI = Board.tilesets.size();
        Board.tilesets.add(set);
        countdown = new CountDownLatch(players.size());
        for (Player p : players.values()) {
            p.pipe.sink().write(ByteBuffer.wrap(new byte[]{6}));
        }
        countdown.await();
        countdown = new CountDownLatch(players.size());
        for (Player p : players.values()) {
            p.pipe.sink().write(ByteBuffer.wrap(new byte[]{5, (byte)Board.CHARI}));
        }
        countdown.await();
    }
    private static void saveTilesets() throws Exception {
        String location = "usersets.tset";
        System.out.print("Use default save location? (Y/n) ");
        if (sc.nextLine().toLowerCase().matches("(n|no)")) {
            while (true) {
                System.out.print("Enter file location: ");
                String l = sc.nextLine();
                if (l.equalsIgnoreCase("cancel")) {
                    System.out.println("reverting to default location");
                    break;
                }
                if (!l.equals("tilesets.txt")) {
                    location = l;
                    break;
                }
                System.out.println("can't save there");
            }
        }
        System.out.printf("About to save tilesets to \"%s\"%nConfirm saving? (this will delete any tilesets from this file that are not currently loaded) (y/N) ", location);
        if (!sc.nextLine().toLowerCase().matches("(y|yes)")) {
            System.out.println("aborting");
            return;
        }
        String f = "";
        for (char[] set : Board.tilesets) {
            f += String.format("%c%c%c%c\n", set[1], set[2], set[3], set[4]);
        }
        f = f.substring(0, f.length() - 1);
        Files.write(Pathof(location), f.getBytes(StandardCharsets.UTF_8));
        System.out.println("successfully saved tilesets");
    }
    private static void tileset() throws Exception {
        while (true) {
            int choice = getRestrictNum(
                "Please select an option or enter \"done\" when finished:" + Util.NL + " (1) use already loaded tileset" + Util.NL + " (2) load tilesets from file" + Util.NL + " (3) create new tileset" + Util.NL + " (4) save tilesets to file:" + Util.NL + "> ", 1, 4);
            if (choice == 0) {
                return;
            }
            switch (choice) {
                case 1:
                    pickTileset();
                    break;
                case 2:
                    loadTilesets();
                    break;
                case 3:
                    makeTileset();
                    break;
                case 4:
                    saveTilesets();
                    break;
            }
        }
    }
    private static void clcommand(String line) throws Exception {
        if (line.equalsIgnoreCase("msg")) {
            System.out.print("Welcome to JCultureMultiOL, the only (as of May 2024) online" + Util.NL + "implementation of the game \"Culture\" by Secret Lab Pty. Ltd.." + Util.NL + "Enter \"help\" for a useful information" + Util.NL);
            return;
        }
        if (line.equalsIgnoreCase("list")) {
            for (Team team : Teams.teams) {
                System.out.print(team.id + ": ");
                System.out.println(team);
                // System.out.println(Color.DEFAULT);
                for (Player p : players.values()) {
                    if (p.team.id == team.id) {
                        System.out.println("  " + p.name + "(" + p.id + ")");
                    }
                }
            }
        } else if (line.equalsIgnoreCase("setteam")) {
            clcommand("list");
            int pid;
            while (true) {
                try {
                    System.out.print("Enter player id: ");
                    String l = sc.nextLine();
                    if (l.equalsIgnoreCase("cancel")) return;
                    pid = Integer.parseInt(l);
                    if (pid < 0 || pid >= players.size()) {
                        System.out.println("out of range");
                        continue;
                    }
                    break;
                } catch (Exception E) {
                    System.out.println("malformed");
                }
            }
            int tid;
            while (true) {
                try {
                    System.out.print("Enter team id: ");
                    String l = sc.nextLine();
                    if (l.equalsIgnoreCase("cancel")) return;
                    tid = Integer.parseInt(l);
                    if (tid < 0 || tid > 5) {
                        System.out.println("out of range");
                        continue;
                    }
                    break;
                } catch (Exception E) {
                    System.out.println("malformed");
                }
            }
            Player pl = players.get(pid);
            Team nteam = Teams.teams[tid];
            System.out.printf("\"%s\" has switched from team %s%s%s to team %s%s%s%n", pl.name, pl.team.color, pl.team.name, Color.DEFAULT, nteam.color, nteam.name, Color.DEFAULT);
            pl.team = nteam;
            countdown = new CountDownLatch(players.size());
            for (Player p : players.values()) {
                p.pipe.sink().write(ByteBuffer.wrap(new byte[]{2, (byte)pid, (byte)tid}));
            }
            countdown.countDown();
            countdown.await();
        } else if (line.equalsIgnoreCase("tileset")) {
            tileset();
        } else if (line.equalsIgnoreCase("options")) {
            options();
        } else if (line.equalsIgnoreCase("help")) {
            System.out.println("HELP MENU");
            System.out.printf(
                "- help    -- shows this menu%n"+
                "- msg     -- shows the welcome message%n"+
                "- stop    -- stops the server%n"+
                "- start   -- starts the game, will bring up a prompt for board dimensions%n"+
                "- list    -- lists all players in the session%n"+
                "- setteam -- switches what team a player is on,%n"+
                "              prompts for player id and team id,%n"+
                "              automatically runs the \"list\" command%n"+
                "- tileset -- opens the prompt to change the game's tileset%n"+
                "\nany command with a prompt may be canceled by entering \"cancel\"%n%n"
	    );
        } else {
            System.out.println("unrecognized\n");
            clcommand("help");
        }
    }
    private static void cli() throws Exception {
        clcommand("msg");
        while (true) {
            String line = sc.nextLine();
            if (line.length() == 0) continue;
            if (line.equalsIgnoreCase("stop")) {
                System.out.println("STOPPING");
                gamestate = -1;
                countdown = new CountDownLatch(players.size());
                for (Player p : players.values()) {
                    p.pipe.sink().write(ByteBuffer.wrap(new byte[]{0}));
                }
                countdown.await();
                return;
            } else if (line.equalsIgnoreCase("start")) {
                if (players.isEmpty()) {
                    System.out.println("Cannot start game without any players");
                    continue;
                }
                itcw = getRestrictNum("Enter width: ", 1, 26);
                if (itcw < 1) continue;
                itch = getRestrictNum("Enter height: ", 1, 26);
                if (itch < 1) continue;
                if (itcw == 1 && itch == 1) {
                    System.out.println("1-by-1 boards are not allowed");
                    continue;
                }
                gamestate = 1;
                itcp = players.size();
                Teams.reset();
                for (Player p : players.values()) {
                    Teams.teams[p.team.id].pcount ++;
                }
                game = new Game(itcw, itch);
                countdown = new CountDownLatch(itcp);
                for (Player p : players.values()) {
                    p.pipe.sink().write(ByteBuffer.wrap(new byte[]{1}));
                }
                countdown.await();
                runloop();
                gamestate = 0;
            } else {
                clcommand(line);
            }
        }
    }
    private static void start(int port) {
        try (ServerSocket serv = new ServerSocket(port)) {
            Thread br = new Thread(){public void run(){try{Host.backrunner();}catch(Exception E){E.printStackTrace();}}};
            br.setDaemon(true);
            br.start();
            while (true) {
                Socket sock = serv.accept();
                Thread t = new Thread(){public void run(){try{serve(sock);}catch(Exception E){E.printStackTrace();}}};
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception E) {
            // E.printStackTrace();
            // throw new IllegalStateException(E);
        }
    }
    private static boolean checkPassword(Socket sock) throws Exception {
        InputStream sIn = sock.getInputStream();
        OutputStream sOut = sock.getOutputStream();
        while (true) {
            byte[] bbuf = new byte[read(sIn)];
            read(sIn, bbuf);
            String passin = new String(bbuf);
            if (!passin.equals(hostpass)) {
                sOut.write(0);
                if (read(sIn) == 0) {
                    return false;
                }
                continue;
            }
            sOut.write(1);
            return true;
        }
    }
    private static void serve(Socket sock) throws Exception {
        InputStream sIn = sock.getInputStream();
        OutputStream sOut = sock.getOutputStream();
        int conncode = read(sIn);
        if (conncode == 0x66) {
            sOut.write(usingPass ? 1 : 0);
            sOut.write(hostname.length());
            sOut.write(hostname.getBytes());
	    sOut.write((Host.VER_MAJOR >> 8) & 0xff);
	    sOut.write(Host.VER_MAJOR & 0xff);
	    sOut.write((Host.VER_MINOR >> 8) & 0xff);
	    sOut.write(Host.VER_MINOR & 0xff);
	    sOut.write((Host.VER_INFRAMINOR >> 8) & 0xff);
	    sOut.write(Host.VER_INFRAMINOR & 0xff);
	    sOut.flush();
	    //sIn.read();
	    //Thread.sleep(500);
            sock.close();
            return;
        }
        if (conncode == 0x22) {
            int gsid = (read(sIn)<<8) | read(sIn);
            if (connections.containsKey(gsid)) {
                Connection conn = connections.get(gsid);
                if (conn.s2 != null) {
                    sOut.write(0);
                    sock.close();
                    return;
                }
                conn.s2(sock);
                sOut.write(1);
                conn.cf1.complete(null);
                conn.cf2.get();
                handle2(players.get(conn.pid));
            } else {
                sOut.write(0);
                sock.close();
            }
            return;
        }
        if (conncode == 0x44) {
            // synchronized(Teams.teams) {
            //     if (Teams.teams.size() > 5) {
            //         sOut.write(2);
            //         sock.close();
            //         return;
            //     }
            // }
            if (gamestate != 0) {
                sOut.write(2);
                sock.close();
                return;
            }
            sOut.write(0);
            int sid;
            synchronized(SID_LOCK) {
                sid = CSID++;
            }
            Connection conn = new Connection(sid, sock, null);
            connections.put(sid, conn);
            sOut.write(sid >> 8);
            sOut.write(sid & 0xff);
            sOut.flush();
            try {
                conn.cf1.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException T) {
                sock.close();
                connections.remove(sid);
                return;
            }
            if (usingPass) {
                sOut.write(1);
                if (!checkPassword(sock)) {
                    sock.close();
                    return;
                }
            } else {
                sOut.write(0);
            }
            byte[] bb = new byte[read(sIn)];
            read(sIn, bb);
            String cliname = new String(bb);
            // Color cc;
            int id = sid%256;
            // synchronized(Teams.teams) {
            //     id = Teams.teams.size();
            //     cc = new Color(id);
            //     Teams.teams.put(id, new Team(id, cc, cliname));
            // }
            sOut.write(id);
            sOut.write(id%6);
            conn.pid = id;
            Player p = new Player(id, Teams.teams[id%6], cliname, conn);
            System.out.printf("\"%s\" joined %s%s%s\n", p.name, p.team.color, p.team.name, Color.DEFAULT);
            preloop(p);
            // sOut.write(cc.red);
            // sOut.write(cc.green);
            // sOut.write(cc.blue);
        }
    }
    private static void preloop(Player player) throws Exception {
        InputStream sIn = player.conn.s1I;
        OutputStream sOut = player.conn.s1O;
        synchronized(players) {
            for (Player p : players.values()) {
                Socket s = p.conn.s1;
                OutputStream pOut = s.getOutputStream();
                pOut.write(0x02);
                pOut.write(player.name.length());
                pOut.write(player.name.getBytes());
                pOut.write(player.id);
                pOut.write(player.team.id);
                sOut.write(0x02);
                sOut.write(p.name.length());
                sOut.write(p.name.getBytes());
                sOut.write(p.id);
                sOut.write(p.team.id);
            }
            players.put(player.id, player);
        }
        sOut.write(6);
        sOut.write(Board.tilesets.size());
        for (char[] set : Board.tilesets) {
            for (int i = 1; i < 5; i ++) {
                sOut.write(set[i]>>8);
                sOut.write(set[i]&0xff);
            }
        }
        sOut.write(5);
        sOut.write(Board.CHARI);
        player.conn.cf2.complete(null);
        while (true) {
            byte[] bb = new byte[1];
            player.pipe.source().read(ByteBuffer.wrap(bb));
            // System.out.printf("PLAYER %d COMM %d\n", player.id, bb[0]);
            int pc = bb[0];
            switch (pc) {
                case 0:
                    sOut.write(0);
                    player.conn.close(true);
                    countdown.countDown();
                    return;
                case 1: {
                    sOut.write(1);
                    read(sIn);
                    sOut.write(itcw);
                    sOut.write(itch);
                    countdown.countDown();
                    countdown.await();
                    gameloop(player);
                    break;
                }
                case 2: {
                    byte[] buf = new byte[3];
                    player.pipe.source().read(ByteBuffer.wrap(buf));
                    sOut.write(3);
                    sOut.write(buf[0]);
                    sOut.write(buf[1]);
                    countdown.countDown();
                    countdown.await();
                    break;
                }
                case 4: {
                    byte[] buf = new byte[2];
                    player.pipe.source().read(ByteBuffer.wrap(buf));
                    sOut.write(4);
                    sOut.write(buf[0]);
                    countdown.countDown();
                    countdown.await();
                    break;
                }
                case 5: {
                    byte[] buf = new byte[1];
                    player.pipe.source().read(ByteBuffer.wrap(buf));
                    sOut.write(5);
                    sOut.write(buf[0]);
                    countdown.countDown();
                    countdown.await();
                    break;
                }
                case 6: {
                    sOut.write(6);
                    sOut.write(Board.tilesets.size());
                    for (char[] set : Board.tilesets) {
                        for (int i = 1; i < 5; i ++) {
                            sOut.write(set[i]>>8);
                            sOut.write(set[i]&0xff);
                        }
                    }
                    countdown.countDown();
                    countdown.await();
                    break;
                }
            }
        }
    }
    private static void gameloop(Player player) throws Exception {
        InputStream sIn = player.conn.s1I;
        OutputStream sOut = player.conn.s1O;
        SourceChannel src = player.pipe.source();
        SinkChannel snk = player.pipe.sink();
        while (true) {
            byte[] buf1 = new byte[1];
            byte[] buf2 = new byte[3];
            src.read(ByteBuffer.wrap(buf1));
            int comm = buf1[0];
            if (comm == 0) {
                sOut.write(0);
            } else if (comm == 1) {
                sOut.write(1);
                int x, y;
                while (true) {
                    x = read(sIn);
                    y = read(sIn);
                    if (game.validate(x, y, player.team.id)) {
                        sOut.write(1);
                        break;
                    } else {
                        System.out.println(player.team.id);
                        System.out.println(game.board.board[y][x].team);
                        sOut.write(0);
                    }
                }
                buf2[0] = (byte)x;
                buf2[1] = (byte)y;
                buf2[2] = (byte)player.team.id;
                snk.write(ByteBuffer.wrap(buf2));
            } else if (comm == 2) {
                src.read(ByteBuffer.wrap(buf2));
                sOut.write(buf2);
            } else if (comm == 3) {
                return;
            }
        }
    }
    private static int read(InputStream in) throws Exception {
        return read(null, in);
    }
    private static int read(Connection conn, InputStream in) throws Exception {
        int r = -1;
        try {
            r = in.read();
        } catch (SocketException SE) {
            crash(conn, SE);
        }
        if (r < 0) crash(conn);
        return r;
    }
    private static int read(InputStream in, byte[] buf) throws Exception {
        return read(null, in, buf);
    }
    private static int read(Connection conn, InputStream in, byte[] buf) throws Exception {
        try {
            if (in.read(buf) != buf.length) crash(conn);
        } catch (SocketException SE) {
            crash(conn, SE);
        }
        return buf.length;
    }
    private static void crash(Connection conn) throws Exception {
        crash(conn, null);
    }
    private static void crash(Connection conn, Throwable cause) throws Exception {
        if (conn != null && conn.cgone()) {
            if (cause != null) {throw new ClientGoneException(cause);}
            throw new ClientGoneException();
        }
        System.out.println();
        System.out.println("GAME CRASHED");
        System.out.println();
        throw new IllegalStateException();
    }
    public static void main(String[] args) throws Exception {
        Color.start();
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("google.com", 80));
        System.out.println(socket.getLocalAddress());
        socket.close();
        System.out.print("Enter port: ");
        int port = Integer.parseInt(sc.nextLine());
        System.out.print("Enter name: ");
        hostname = sc.nextLine();
        System.out.print("use password? (y/N) ");
        if (sc.nextLine().toLowerCase().matches("(y|yes)")) {
            usingPass = true;
        }
        if (usingPass) {
            System.out.print("Enter password: ");
            hostpass = sc.nextLine();
        }
        servingThread = new Thread(){public void run(){Host.start(port);}};
        servingThread.setDaemon(true);
        servingThread.start();
        cli();
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
            if (itemd.iid == GRULES_SERVER) {
                GR_server = itemd.getToggleState();
            }
        }
    }
    private static boolean GR_server = false;
    private static final Menu MENU;
    private static final int
    GRULES_SERVER = 0;
    static {
        MenuFrame top = new MenuFrame("Host Options");
        {
            MenuFrame rules = new MenuFrame("Game Rules");
            top.addItem("game rules", ItemData.Group(rules));
            rules.addItem("server mode", ItemData.Toggle(GR_server).withIID(GRULES_SERVER));
        }
        MENU = new Menu(top);
    }
}
