package engine

import (
	"os"
	"testing"
)

func TestEval(t *testing.T) {
	v := Vars{ScreenW: 1000, ScreenH: 800, AreaW: 1000, AreaH: 760, ImageW: 40, ImageH: 40, Random: 50, RandS: 30, Scale: 1}
	cases := map[string]int{
		"0":                                0,
		"5+random/10":                      10,
		"screenW-imageW":                   960,
		"areaH-imageH":                     720,
		"-imageW":                          -40,
		"random/20-5":                      -2, // 2.5-5 = -2.5 -> trunc -2
		"random/100*screenW":               500,
		"iif(random>40,1,2)":               1,
		"iif(random<=40,1,2)":              2,
		"Convert(7/2, System.Int32)":       3,
		"(1+2)*3":                          9,
		"10%3":                             1,
		"iif(random>10 and random<60,5,6)": 5,
		"iif(not(random>10),5,6)":          6,
	}
	for src, want := range cases {
		got, err := Eval(src, v)
		if err != nil || got != want {
			t.Errorf("Eval(%q) = %d, %v; want %d", src, got, err, want)
		}
	}
	if _, err := Eval("1+", v); err == nil {
		t.Error("expected error for incomplete expression")
	}
}

func loadPet(t *testing.T, path string) *Pet {
	f, err := os.Open(path)
	if err != nil {
		t.Skip("pet file missing: " + path)
	}
	defer f.Close()
	p, err := Parse(f)
	if err != nil {
		t.Fatal(err)
	}
	return p
}

// Runs the pet for many ticks and checks invariants: stays on screen, valid frames,
// sane intervals, and that it actually moves and changes animation.
func TestRunPet(t *testing.T) {
	path := os.Getenv("PET_TXT")
	if path == "" {
		path = "../build/mareep/pet.txt"
	}
	p := loadPet(t, path)
	env := Env{ScreenW: 1280, ScreenH: 800, AreaW: 1280, AreaH: 760}
	for seed := int64(1); seed <= 20; seed++ {
		e := New(p, env, 1, seed)
		e.Spawn()
		names := map[string]bool{}
		minX, maxX := 1e9, -1e9
		for i := 0; i < 20000; i++ {
			e.Tick()
			names[e.CurrentName()] = true
			if e.Frame < 0 || e.Frame >= p.TilesX*p.TilesY {
				t.Fatalf("seed %d tick %d: frame %d out of range", seed, i, e.Frame)
			}
			if e.X < -0.001 || e.X > env.AreaW-float64(e.ImageW())+0.001 || e.Y < -0.001 || e.Y > env.AreaH-float64(e.ImageH())+0.001 {
				t.Fatalf("seed %d tick %d: pet left the screen at %.1f,%.1f (anim %s)", seed, i, e.X, e.Y, e.CurrentName())
			}
			if e.Interval < 15 || e.Interval > 5000 {
				t.Fatalf("interval %d", e.Interval)
			}
			if e.X < minX {
				minX = e.X
			}
			if e.X > maxX {
				maxX = e.X
			}
		}
		if maxX-minX < 100 {
			t.Errorf("seed %d: pet barely moved (%.0f..%.0f)", seed, minX, maxX)
		}
		if len(names) < 3 {
			t.Errorf("seed %d: only %d distinct animations: %v", seed, len(names), names)
		}
	}
}

func TestDragFall(t *testing.T) {
	path := os.Getenv("PET_TXT")
	if path == "" {
		path = "../build/mareep/pet.txt"
	}
	p := loadPet(t, path)
	env := Env{ScreenW: 1280, ScreenH: 800, AreaW: 1280, AreaH: 760}
	e := New(p, env, 1, 7)
	e.Spawn()
	e.Tick()
	e.BeginDrag()
	e.DragTo(500, 100)
	for i := 0; i < 5; i++ {
		e.Tick()
	}
	if e.X != 500 || e.Y != 100 {
		t.Fatalf("pet moved while dragged: %.1f,%.1f", e.X, e.Y)
	}
	e.EndDrag()
	if p.FallID != 0 && e.CurrentName() == "" {
		t.Fatal("no animation after release")
	}
	landed := false
	for i := 0; i < 2000; i++ {
		e.Tick()
		if e.Y >= e.floorY()-0.5 {
			landed = true
			break
		}
	}
	if !landed {
		t.Fatalf("pet never reached the floor (y=%.1f, anim %s)", e.Y, e.CurrentName())
	}
}
