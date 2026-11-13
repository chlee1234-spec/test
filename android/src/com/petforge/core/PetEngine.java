package com.petforge.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * desktopPet animation state machine (Java port of engine/engine.go).
 * No Android dependencies, so it can be tested on a desktop JVM.
 */
public final class PetEngine {
    public static final class Next {
        public int prob;
        public String only;
        public int id;
    }

    public static final class Move {
        public String x, y, interval;
        public double offsetY, opacity;
    }

    public static final class Anim {
        public int id;
        public String name;
        public Move start = new Move(), end = new Move();
        public String repeat = "0";
        public int repeatFrom;
        public boolean flip;
        public int[] frames = new int[0];
        public List<Next> next = new ArrayList<Next>();
        public List<Next> border = new ArrayList<Next>();
        public List<Next> grav = new ArrayList<Next>();
    }

    public static final class Spawn {
        public int id, prob, next;
        public String x, y;
    }

    public static final class Pet {
        public String name = "pet";
        public int tilesX, tilesY, cellW, cellH;
        public Map<Integer, Anim> anims = new HashMap<Integer, Anim>();
        public List<Spawn> spawns = new ArrayList<Spawn>();
        public int dragId, fallId;
    }

    // ---------------------------------------------------------------- parsing

    private static int atoi(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double atof(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Pet parse(InputStream in) throws IOException {
        Pet p = new Pet();
        Anim cur = null;
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        int ln = 0;
        while ((line = r.readLine()) != null) {
            ln++;
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            String[] f = line.split("\t", -1);
            String k = f[0];
            if (k.equals("V")) {
                continue;
            } else if (k.equals("NAME")) {
                p.name = f[1];
            } else if (k.equals("IMG")) {
                p.tilesX = atoi(f[1]);
                p.tilesY = atoi(f[2]);
                p.cellW = atoi(f[3]);
                p.cellH = atoi(f[4]);
            } else if (k.equals("S")) {
                Spawn s = new Spawn();
                s.id = atoi(f[1]);
                s.prob = atoi(f[2]);
                s.next = atoi(f[3]);
                s.x = f[4];
                s.y = f[5];
                p.spawns.add(s);
            } else if (k.equals("A")) {
                cur = new Anim();
                cur.id = atoi(f[1]);
                cur.name = f[2];
                p.anims.put(cur.id, cur);
                String lower = cur.name.trim().toLowerCase();
                if (lower.equals("drag")) p.dragId = cur.id;
                else if (lower.equals("fall")) p.fallId = cur.id;
            } else if (cur == null) {
                throw new IOException("line " + ln + ": " + k + " outside animation");
            } else if (k.equals("ST") || k.equals("EN")) {
                Move m = new Move();
                m.x = f[1];
                m.y = f[2];
                m.offsetY = atof(f[3]);
                m.opacity = atof(f[4]);
                m.interval = f[5];
                if (k.equals("ST")) cur.start = m;
                else cur.end = m;
            } else if (k.equals("Q")) {
                cur.repeat = f[1];
                cur.repeatFrom = atoi(f[2]);
                cur.flip = f[3].equals("flip");
            } else if (k.equals("F")) {
                String[] fr = f[1].trim().split("\\s+");
                cur.frames = new int[fr.length];
                for (int i = 0; i < fr.length; i++) cur.frames[i] = atoi(fr[i]);
            } else if (k.equals("N") || k.equals("B") || k.equals("G")) {
                Next n = new Next();
                n.prob = atoi(f[1]);
                n.only = f[2];
                n.id = atoi(f[3]);
                if (k.equals("N")) cur.next.add(n);
                else if (k.equals("B")) cur.border.add(n);
                else cur.grav.add(n);
            } else {
                throw new IOException("line " + ln + ": unknown record " + k);
            }
        }
        if (p.anims.isEmpty() || p.spawns.isEmpty() || p.tilesX <= 0 || p.tilesY <= 0) {
            throw new IOException("pet.txt incomplete");
        }
        for (Anim a : p.anims.values()) {
            if (a.frames.length == 0) throw new IOException("animation " + a.id + " has no frames");
            List<List<Next>> lists = new ArrayList<List<Next>>();
            lists.add(a.next);
            lists.add(a.border);
            lists.add(a.grav);
            for (List<Next> l : lists)
                for (Next n : l)
                    if (!p.anims.containsKey(n.id)) throw new IOException("animation " + a.id + " -> missing " + n.id);
        }
        for (Spawn s : p.spawns)
            if (!p.anims.containsKey(s.next)) throw new IOException("spawn " + s.id + " -> missing " + s.next);
        return p;
    }

    // ----------------------------------------------------------------- engine

    public final Pet pet;
    public double scale;
    public double screenW, screenH, areaW, areaH;
    private final Random rng;
    private final double randS;

    // Output state, valid after tick()/spawn().
    public double x, y, offsetY, opacity = 1;
    public int frame, interval = 200;
    public boolean flipped, dragging;

    private Anim cur;
    private int step, total;
    private double sx, sy, ex, ey;
    private int sint, eint;

    public PetEngine(Pet pet, double scale, long seed) {
        this.pet = pet;
        this.scale = scale <= 0 ? 1 : scale;
        this.rng = new Random(seed);
        this.randS = 10 + rng.nextInt(80);
    }

    public void setEnv(double screenW, double screenH, double areaW, double areaH) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.areaW = areaW;
        this.areaH = areaH;
        clamp();
    }

    public double imageW() {
        return pet.cellW * scale;
    }

    public double imageH() {
        return pet.cellH * scale;
    }

    public double floorY() {
        return areaH - imageH();
    }

    public String currentName() {
        return cur == null ? "" : cur.name;
    }

    private Expr.Vars vars() {
        Expr.Vars v = new Expr.Vars();
        v.screenW = screenW;
        v.screenH = screenH;
        v.areaW = areaW;
        v.areaH = areaH;
        v.imageW = imageW();
        v.imageH = imageH();
        v.random = rng.nextInt(100);
        v.randS = randS;
        v.scale = scale;
        return v;
    }

    private double eval(String src) {
        return Expr.eval(src, vars());
    }

    private void clamp() {
        x = Math.max(0, Math.min(x, areaW - imageW()));
        y = Math.max(0, Math.min(y, floorY()));
    }

    public void spawn() {
        int sum = 0;
        for (Spawn s : pet.spawns) sum += s.prob;
        Spawn pick = pet.spawns.get(0);
        if (sum > 0) {
            int v = rng.nextInt(sum) + 1, acc = 0;
            for (Spawn s : pet.spawns) {
                acc += s.prob;
                if (acc >= v) {
                    pick = s;
                    break;
                }
            }
        }
        x = eval(pick.x);
        y = eval(pick.y);
        flipped = false;
        clamp();
        start(pick.next);
    }

    private void start(int id) {
        Anim a = pet.anims.get(id);
        if (a == null) a = pet.anims.get(pet.spawns.get(0).next);
        cur = a;
        step = 0;
        sx = eval(a.start.x) * scale;
        sy = eval(a.start.y) * scale;
        ex = eval(a.end.x) * scale;
        ey = eval(a.end.y) * scale;
        sint = (int) eval(a.start.interval);
        eint = (int) eval(a.end.interval);
        int rep = a.repeat.trim().length() == 0 ? 0 : (int) eval(a.repeat);
        if (rep < 0) rep = 0;
        int n = a.frames.length;
        int rf = (a.repeatFrom < 0 || a.repeatFrom >= n) ? 0 : a.repeatFrom;
        total = n + (n - rf) * rep;
        if (total < 1) total = 1;
        interval = clampInterval(sint);
    }

    private static int clampInterval(int v) {
        return v < 15 ? 15 : v;
    }

    private static boolean onlyMatches(String only, String where) {
        only = only.trim();
        if (only.length() == 0 || only.equals("none")) return true;
        boolean horiz = where.equals("horizontal") || where.equals("window") || where.equals("taskbar");
        boolean vert = where.equals("vertical") || where.equals("windowSide") || where.equals("petSide");
        return only.equals(where) || (horiz && only.equals("horizontal+")) || (vert && only.equals("vertical+"));
    }

    private int pick(List<Next> list, String where) {
        int sum = 0;
        for (Next n : list) if (onlyMatches(n.only, where)) sum += n.prob;
        if (sum <= 0) return -1;
        int v = rng.nextInt(sum) + 1, acc = 0;
        for (Next n : list) {
            if (!onlyMatches(n.only, where)) continue;
            acc += n.prob;
            if (acc >= v) return n.id;
        }
        return -1;
    }

    private int frameAt(int st) {
        int n = cur.frames.length;
        int rf = (cur.repeatFrom < 0 || cur.repeatFrom >= n) ? 0 : cur.repeatFrom;
        if (st < n) return cur.frames[st];
        return cur.frames[(st - n + rf) % (n - rf) + rf];
    }

    public void beginDrag() {
        dragging = true;
        if (pet.dragId != 0) start(pet.dragId);
    }

    public void dragTo(double nx, double ny) {
        x = Math.max(0, Math.min(nx, areaW - imageW()));
        y = Math.max(0, Math.min(ny, areaH - imageH()));
    }

    public void endDrag() {
        dragging = false;
        if (y < floorY() - 1 && pet.fallId != 0) {
            start(pet.fallId);
            return;
        }
        y = Math.min(y, floorY());
        newNext(cur, "none");
    }

    private void newNext(Anim a, String where) {
        int id = a == null ? -1 : pick(a.next, where);
        if (id < 0) spawn();
        else start(id);
    }

    private boolean hit(Anim a, String where) {
        int id = pick(a.border, where);
        if (id < 0) return false;
        start(id);
        return true;
    }

    /** Advance one step; afterwards draw {@link #frame} at (x, y+offsetY), mirrored if flipped. */
    public void tick() {
        if (cur == null) spawn();
        Anim a = cur;
        frame = frameAt(Math.min(step, total - 1));
        double prog = total > 0 ? (double) step / total : 0;
        interval = clampInterval(sint + (int) ((eint - sint) * prog));
        opacity = a.start.opacity + (a.end.opacity - a.start.opacity) * prog;
        offsetY = (a.start.offsetY + (a.end.offsetY - a.start.offsetY) * prog) * scale;

        if (dragging) {
            step++;
            if (step >= total) step = 0;
            return;
        }

        double mx = sx, my = sy;
        if (total > 1) {
            double f = (double) step / (total - 1);
            mx += (ex - sx) * f;
            my += (ey - sy) * f;
        }
        if (flipped) mx = -mx;

        double w = imageW(), floor = floorY();
        if (mx < 0 && x + mx < 0) {
            x = 0;
            if (hit(a, "vertical")) return;
            mx = 0;
        } else if (mx > 0 && x + w + mx > areaW) {
            x = areaW - w;
            if (hit(a, "vertical")) return;
            mx = 0;
        }
        if (my < 0 && y + my < 0) {
            y = 0;
            if (hit(a, "horizontal")) return;
            my = 0;
        } else if (my > 0 && y + my > floor) {
            y = floor;
            if (hit(a, "taskbar")) return;
            my = 0;
        }
        x += mx;
        y += my;
        step++;

        if (!a.grav.isEmpty() && y < floor - 1) {
            int id = pick(a.grav, "none");
            if (id >= 0) {
                start(id);
                return;
            }
        }
        if (step >= total) {
            if (a.flip) flipped = !flipped;
            newNext(a, "none");
        }
    }
}
