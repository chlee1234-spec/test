package com.petforge.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
        public byte[] png;   // sprite sheet, flattened on magenta (transparency key)
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

    private static Element child(Element e, String name) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && ((Element) n).getTagName().equals(name)) return (Element) n;
        return null;
    }

    private static List<Element> children(Element e, String name) {
        List<Element> out = new ArrayList<Element>();
        if (e == null) return out;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && ((Element) n).getTagName().equals(name)) out.add((Element) n);
        return out;
    }

    private static String text(Element e, String name, String def) {
        Element c = e == null ? null : child(e, name);
        if (c == null) return def;
        String t = c.getTextContent().trim();
        return t.length() == 0 ? def : t.replaceAll("\\s+", " ");
    }

    private static Move move(Element e) {
        Move m = new Move();
        m.x = text(e, "x", "0");
        m.y = text(e, "y", "0");
        m.offsetY = atof(text(e, "offsety", "0"));
        m.opacity = atof(text(e, "opacity", "1"));
        m.interval = text(e, "interval", "200");
        return m;
    }

    private static void nexts(Element parent, List<Next> out) {
        for (Element n : children(parent, "next")) {
            Next x = new Next();
            x.prob = atoi(n.getAttribute("probability"));
            x.only = n.getAttribute("only").length() == 0 ? "none" : n.getAttribute("only");
            x.id = atoi(n.getTextContent());
            out.add(x);
        }
    }

    /** Parses a desktopPet animations.xml (schema https://esheep.petrucci.ch/). */
    public static Pet parseXml(InputStream in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        Element root = f.newDocumentBuilder().parse(in).getDocumentElement();
        Pet p = new Pet();
        Element hdr = child(root, "header");
        p.name = text(hdr, "petname", text(hdr, "title", "pet"));
        Element img = child(root, "image");
        if (img == null) throw new IOException("no <image>");
        p.tilesX = atoi(text(img, "tilesx", "0"));
        p.tilesY = atoi(text(img, "tilesy", "0"));
        p.png = Base64.getMimeDecoder().decode(text(img, "png", "").replace(" ", ""));
        if (p.png.length < 24 || p.tilesX <= 0 || p.tilesY <= 0) throw new IOException("bad image");
        // PNG IHDR: width/height are big-endian ints at byte 16 and 20.
        int w = ((p.png[16] & 255) << 24) | ((p.png[17] & 255) << 16) | ((p.png[18] & 255) << 8) | (p.png[19] & 255);
        int h = ((p.png[20] & 255) << 24) | ((p.png[21] & 255) << 16) | ((p.png[22] & 255) << 8) | (p.png[23] & 255);
        p.cellW = w / p.tilesX;
        p.cellH = h / p.tilesY;
        for (Element sp : children(child(root, "spawns"), "spawn")) {
            Spawn s = new Spawn();
            s.id = atoi(sp.getAttribute("id"));
            s.prob = atoi(sp.getAttribute("probability"));
            s.next = atoi(text(sp, "next", "0"));
            s.x = text(sp, "x", "0");
            s.y = text(sp, "y", "0");
            p.spawns.add(s);
        }
        for (Element an : children(child(root, "animations"), "animation")) {
            Anim cur = new Anim();
            cur.id = atoi(an.getAttribute("id"));
            cur.name = text(an, "name", "");
            cur.start = move(child(an, "start"));
            cur.end = move(child(an, "end"));
            Element seq = child(an, "sequence");
            if (seq == null) throw new IOException("animation " + cur.id + " has no sequence");
            cur.repeat = seq.getAttribute("repeat").length() == 0 ? "0" : seq.getAttribute("repeat");
            cur.repeatFrom = atoi(seq.getAttribute("repeatfrom"));
            cur.flip = text(seq, "action", "none").equals("flip");
            List<Element> fr = children(seq, "frame");
            cur.frames = new int[fr.size()];
            for (int i = 0; i < cur.frames.length; i++) cur.frames[i] = atoi(fr.get(i).getTextContent());
            nexts(seq, cur.next);
            nexts(child(an, "border"), cur.border);
            nexts(child(an, "gravity"), cur.grav);
            p.anims.put(cur.id, cur);
            String lower = cur.name.trim().toLowerCase();
            if (lower.equals("drag")) p.dragId = cur.id;
            else if (lower.equals("fall")) p.fallId = cur.id;
        }
        if (p.anims.isEmpty() || p.spawns.isEmpty()) throw new IOException("animations.xml incomplete");
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
