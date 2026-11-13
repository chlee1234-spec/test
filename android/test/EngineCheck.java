import com.petforge.core.Expr;
import com.petforge.core.PetEngine;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

/** Desktop-JVM check of the Java engine, mirroring engine/engine_test.go. usage: EngineCheck pet.txt */
public class EngineCheck {
    static int failures = 0;

    static void check(boolean ok, String msg) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + msg);
        }
    }

    public static void main(String[] args) throws Exception {
        Expr.Vars v = new Expr.Vars();
        v.screenW = 1000; v.screenH = 800; v.areaW = 1000; v.areaH = 760;
        v.imageW = 40; v.imageH = 40; v.random = 50; v.randS = 30; v.scale = 1;
        Object[][] cases = {
            {"0", 0}, {"5+random/10", 10}, {"screenW-imageW", 960}, {"areaH-imageH", 720},
            {"-imageW", -40}, {"random/20-5", -2}, {"random/100*screenW", 500},
            {"iif(random>40,1,2)", 1}, {"iif(random<=40,1,2)", 2},
            {"Convert(7/2, System.Int32)", 3}, {"(1+2)*3", 9}, {"10%3", 1},
            {"iif(random>10 and random<60,5,6)", 5}, {"iif(not(random>10),5,6)", 6},
        };
        for (Object[] c : cases) {
            int got = Expr.eval((String) c[0], v);
            check(got == (Integer) c[1], "Eval(" + c[0] + ") = " + got + ", want " + c[1]);
        }

        PetEngine.Pet pet = PetEngine.parse(new FileInputStream(args[0]));
        for (long seed = 1; seed <= 20; seed++) {
            PetEngine e = new PetEngine(pet, 1, seed);
            e.setEnv(1280, 800, 1280, 760);
            e.spawn();
            Set<String> names = new HashSet<String>();
            double minX = 1e9, maxX = -1e9;
            for (int i = 0; i < 20000; i++) {
                e.tick();
                names.add(e.currentName());
                if (e.frame < 0 || e.frame >= pet.tilesX * pet.tilesY) {
                    check(false, "seed " + seed + " frame " + e.frame);
                    break;
                }
                if (e.x < -0.001 || e.x > 1280 - e.imageW() + 0.001 || e.y < -0.001 || e.y > 760 - e.imageH() + 0.001) {
                    check(false, "seed " + seed + " tick " + i + " off screen " + e.x + "," + e.y + " " + e.currentName());
                    break;
                }
                if (e.interval < 15 || e.interval > 5000) {
                    check(false, "interval " + e.interval);
                    break;
                }
                minX = Math.min(minX, e.x);
                maxX = Math.max(maxX, e.x);
            }
            check(maxX - minX >= 100, "seed " + seed + " barely moved");
            check(names.size() >= 3, "seed " + seed + " only " + names);
        }

        // drag + fall
        PetEngine e = new PetEngine(pet, 1, 7);
        e.setEnv(1280, 800, 1280, 760);
        e.spawn();
        e.tick();
        e.beginDrag();
        e.dragTo(500, 100);
        for (int i = 0; i < 5; i++) e.tick();
        check(e.x == 500 && e.y == 100, "moved while dragged");
        e.endDrag();
        boolean landed = false;
        for (int i = 0; i < 2000 && !landed; i++) {
            e.tick();
            if (e.y >= e.floorY() - 0.5) landed = true;
        }
        check(landed, "never landed, y=" + e.y + " " + e.currentName());

        System.out.println(failures == 0 ? "ALL OK (" + pet.name + ")" : failures + " failure(s)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
