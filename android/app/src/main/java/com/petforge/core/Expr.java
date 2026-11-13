package com.petforge.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Expression evaluator (same grammar as desktopPet animations.xsd expressionType). */
public final class Expr {
    public static final class Vars {
        public double screenW, screenH, areaW, areaH, imageW, imageH, random, randS, scale = 1;
    }

    private final List<String> toks = new ArrayList<String>();
    private int p;
    private final Vars v;

    private Expr(String src, Vars v) {
        this.v = v;
        lex(src);
    }

    /** Evaluates and truncates to int; returns 0 on malformed input. */
    public static int eval(String src, Vars v) {
        return (int) evalFloat(src, v);
    }

    public static double evalFloat(String src, Vars v) {
        try {
            Expr e = new Expr(src, v);
            double r = e.or();
            if (e.p != e.toks.size()) return 0;
            return r;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private void lex(String s) {
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
            } else if (Character.isDigit(c) || c == '.') {
                int j = i;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                toks.add(s.substring(i, j));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '.')) j++;
                toks.add(s.substring(i, j).toLowerCase(Locale.ROOT));
                i = j;
            } else if ((c == '<' || c == '>') && i + 1 < n
                    && (s.charAt(i + 1) == '=' || (c == '<' && s.charAt(i + 1) == '>'))) {
                toks.add(s.substring(i, i + 2));
                i += 2;
            } else if ("+-*/%(),=<>".indexOf(c) >= 0) {
                toks.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException("bad char " + c);
            }
        }
    }

    private String peek() {
        return p < toks.size() ? toks.get(p) : "";
    }

    private String next() {
        if (p >= toks.size()) throw new IllegalArgumentException("eof");
        return toks.get(p++);
    }

    private static double b(boolean x) {
        return x ? 1 : 0;
    }

    private double or() {
        double l = and();
        while (peek().equals("or")) {
            next();
            double r = and();
            l = b(l != 0 || r != 0);
        }
        return l;
    }

    private double and() {
        double l = not();
        while (peek().equals("and")) {
            next();
            double r = not();
            l = b(l != 0 && r != 0);
        }
        return l;
    }

    private double not() {
        if (peek().equals("not")) {
            next();
            return b(not() == 0);
        }
        return cmp();
    }

    private double cmp() {
        double l = add();
        while (true) {
            String op = peek();
            if (!(op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("=") || op.equals("<>"))) {
                return l;
            }
            next();
            double r = add();
            if (op.equals("<")) l = b(l < r);
            else if (op.equals(">")) l = b(l > r);
            else if (op.equals("<=")) l = b(l <= r);
            else if (op.equals(">=")) l = b(l >= r);
            else if (op.equals("=")) l = b(l == r);
            else l = b(l != r);
        }
    }

    private double add() {
        double l = mul();
        while (peek().equals("+") || peek().equals("-")) {
            String op = next();
            double r = mul();
            l = op.equals("+") ? l + r : l - r;
        }
        return l;
    }

    private double mul() {
        double l = unary();
        while (peek().equals("*") || peek().equals("/") || peek().equals("%")) {
            String op = next();
            double r = unary();
            if (op.equals("*")) l *= r;
            else if (r == 0) l = 0;
            else if (op.equals("/")) l /= r;
            else l = l % r;
        }
        return l;
    }

    private double unary() {
        if (peek().equals("-")) {
            next();
            return -unary();
        }
        if (peek().equals("+")) {
            next();
            return unary();
        }
        return primary();
    }

    private double primary() {
        String t = next();
        char c = t.charAt(0);
        if (Character.isDigit(c) || c == '.') return Double.parseDouble(t);
        if (t.equals("(")) {
            double r = or();
            expect(")");
            return r;
        }
        if (t.equals("screenw")) return v.screenW;
        if (t.equals("screenh")) return v.screenH;
        if (t.equals("areaw")) return v.areaW;
        if (t.equals("areah")) return v.areaH;
        if (t.equals("imagew")) return v.imageW;
        if (t.equals("imageh")) return v.imageH;
        if (t.equals("imagex") || t.equals("imagey")) return -1;
        if (t.equals("random")) return v.random;
        if (t.equals("rands")) return v.randS;
        if (t.equals("scale")) return v.scale;
        if (t.equals("true")) return 1;
        if (t.equals("false")) return 0;
        if (t.equals("iif")) {
            expect("(");
            double c0 = or();
            expect(",");
            double a = or();
            expect(",");
            double bb = or();
            expect(")");
            return c0 != 0 ? a : bb;
        }
        if (t.equals("convert")) {
            expect("(");
            double r = or();
            String type = "";
            if (peek().equals(",")) {
                next();
                type = next();
            }
            expect(")");
            if (type.contains("int") || type.contains("byte")) return r < 0 ? Math.ceil(r) : Math.floor(r);
            return r;
        }
        throw new IllegalArgumentException("unexpected " + t);
    }

    private void expect(String s) {
        if (!next().equals(s)) throw new IllegalArgumentException("expected " + s);
    }
}
