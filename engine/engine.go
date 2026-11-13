// Package engine implements the desktopPet animation state machine on top of
// the pet.txt format (see FORMAT.md). It has no UI dependencies.
package engine

import (
	"bufio"
	"fmt"
	"io"
	"math"
	"math/rand"
	"strconv"
	"strings"
)

type Next struct {
	Prob int
	Only string
	ID   int
}

type Move struct {
	X, Y, Interval string
	OffsetY        float64
	Opacity        float64
}

type Anim struct {
	ID                 int
	Name               string
	Start, End         Move
	Repeat             string
	RepeatFrom         int
	Flip               bool
	Frames             []int
	Next, Border, Grav []Next
}

type Spawn struct {
	ID, Prob, Next int
	X, Y           string
}

type Pet struct {
	Name         string
	TilesX       int
	TilesY       int
	CellW, CellH int
	Anims        map[int]*Anim
	Spawns       []Spawn
	DragID       int
	FallID       int
}

func atoi(s string) int { n, _ := strconv.Atoi(strings.TrimSpace(s)); return n }
func atof(s string) float64 {
	f, _ := strconv.ParseFloat(strings.TrimSpace(s), 64)
	return f
}

// Parse reads a pet.txt file.
func Parse(r io.Reader) (*Pet, error) {
	p := &Pet{Anims: map[int]*Anim{}}
	var cur *Anim
	sc := bufio.NewScanner(r)
	sc.Buffer(make([]byte, 1<<20), 1<<20)
	line := 0
	for sc.Scan() {
		line++
		t := strings.TrimRight(sc.Text(), "\r")
		if t == "" || t[0] == '#' {
			continue
		}
		f := strings.Split(t, "\t")
		need := func(n int) error {
			if len(f) < n {
				return fmt.Errorf("line %d: %s needs %d fields", line, f[0], n)
			}
			return nil
		}
		switch f[0] {
		case "V":
		case "NAME":
			if err := need(2); err != nil {
				return nil, err
			}
			p.Name = f[1]
		case "IMG":
			if err := need(5); err != nil {
				return nil, err
			}
			p.TilesX, p.TilesY, p.CellW, p.CellH = atoi(f[1]), atoi(f[2]), atoi(f[3]), atoi(f[4])
		case "S":
			if err := need(6); err != nil {
				return nil, err
			}
			p.Spawns = append(p.Spawns, Spawn{ID: atoi(f[1]), Prob: atoi(f[2]), Next: atoi(f[3]), X: f[4], Y: f[5]})
		case "A":
			if err := need(3); err != nil {
				return nil, err
			}
			cur = &Anim{ID: atoi(f[1]), Name: f[2]}
			p.Anims[cur.ID] = cur
			switch strings.ToLower(strings.TrimSpace(cur.Name)) {
			case "drag":
				p.DragID = cur.ID
			case "fall":
				p.FallID = cur.ID
			}
		case "ST", "EN":
			if cur == nil {
				return nil, fmt.Errorf("line %d: %s outside animation", line, f[0])
			}
			if err := need(6); err != nil {
				return nil, err
			}
			m := Move{X: f[1], Y: f[2], OffsetY: atof(f[3]), Opacity: atof(f[4]), Interval: f[5]}
			if f[0] == "ST" {
				cur.Start = m
			} else {
				cur.End = m
			}
		case "Q":
			if cur == nil {
				return nil, fmt.Errorf("line %d: Q outside animation", line)
			}
			if err := need(4); err != nil {
				return nil, err
			}
			cur.Repeat, cur.RepeatFrom, cur.Flip = f[1], atoi(f[2]), f[3] == "flip"
		case "F":
			if cur == nil {
				return nil, fmt.Errorf("line %d: F outside animation", line)
			}
			if err := need(2); err != nil {
				return nil, err
			}
			for _, s := range strings.Fields(f[1]) {
				cur.Frames = append(cur.Frames, atoi(s))
			}
		case "N", "B", "G":
			if cur == nil {
				return nil, fmt.Errorf("line %d: %s outside animation", line, f[0])
			}
			if err := need(4); err != nil {
				return nil, err
			}
			n := Next{Prob: atoi(f[1]), Only: f[2], ID: atoi(f[3])}
			switch f[0] {
			case "N":
				cur.Next = append(cur.Next, n)
			case "B":
				cur.Border = append(cur.Border, n)
			default:
				cur.Grav = append(cur.Grav, n)
			}
		default:
			return nil, fmt.Errorf("line %d: unknown record %q", line, f[0])
		}
	}
	if err := sc.Err(); err != nil {
		return nil, err
	}
	if len(p.Anims) == 0 || len(p.Spawns) == 0 || p.TilesX <= 0 || p.TilesY <= 0 {
		return nil, fmt.Errorf("pet.txt incomplete (animations, spawns or IMG missing)")
	}
	for id, a := range p.Anims {
		if len(a.Frames) == 0 {
			return nil, fmt.Errorf("animation %d (%s) has no frames", id, a.Name)
		}
		for _, l := range [][]Next{a.Next, a.Border, a.Grav} {
			for _, n := range l {
				if _, ok := p.Anims[n.ID]; !ok {
					return nil, fmt.Errorf("animation %d refers to missing animation %d", id, n.ID)
				}
			}
		}
	}
	for _, s := range p.Spawns {
		if _, ok := p.Anims[s.Next]; !ok {
			return nil, fmt.Errorf("spawn %d refers to missing animation %d", s.ID, s.Next)
		}
	}
	return p, nil
}

// Env describes the visible area, in pixels. The working area starts at (0,0);
// its bottom edge (AreaH) is the "floor" the pet walks on.
type Env struct {
	ScreenW, ScreenH, AreaW, AreaH float64
}

// Engine runs a pet. Call Tick() every Interval milliseconds and draw
// Frame at (X, Y+OffsetY) mirrored when Flipped.
type Engine struct {
	P     *Pet
	Env   Env
	Scale float64
	Rng   *rand.Rand

	// Output state, valid after Tick/Spawn.
	X, Y     float64 // top-left of the sprite cell
	OffsetY  float64
	Opacity  float64
	Frame    int
	Interval int
	Flipped  bool
	Dragging bool

	cur            *Anim
	step, total    int
	sx, sy, ex, ey float64
	sint, eint     int
	rands          float64
}

func New(p *Pet, env Env, scale float64, seed int64) *Engine {
	if scale <= 0 {
		scale = 1
	}
	e := &Engine{P: p, Env: env, Scale: scale, Rng: rand.New(rand.NewSource(seed)), Opacity: 1}
	e.rands = float64(10 + e.Rng.Intn(80))
	return e
}

func (e *Engine) imageW() float64 { return float64(e.P.CellW) * e.Scale }
func (e *Engine) imageH() float64 { return float64(e.P.CellH) * e.Scale }

// ImageW/ImageH are the displayed sprite size.
func (e *Engine) ImageW() int { return int(math.Round(e.imageW())) }
func (e *Engine) ImageH() int { return int(math.Round(e.imageH())) }

func (e *Engine) vars() Vars {
	return Vars{
		ScreenW: e.Env.ScreenW, ScreenH: e.Env.ScreenH, AreaW: e.Env.AreaW, AreaH: e.Env.AreaH,
		ImageW: e.imageW(), ImageH: e.imageH(),
		Random: float64(e.Rng.Intn(100)), RandS: e.rands, Scale: e.Scale,
	}
}

func (e *Engine) eval(src string) float64 {
	v, err := Eval(src, e.vars())
	if err != nil {
		return 0
	}
	return float64(v)
}

// SetEnv updates the screen size (rotation, resolution change).
func (e *Engine) SetEnv(env Env) {
	e.Env = env
	e.clamp()
}

func (e *Engine) floorY() float64 { return e.Env.AreaH - e.imageH() }

func (e *Engine) clamp() {
	e.X = math.Max(0, math.Min(e.X, e.Env.AreaW-e.imageW()))
	e.Y = math.Max(0, math.Min(e.Y, e.floorY()))
}

// CurrentName returns the current animation name (for debugging/tests).
func (e *Engine) CurrentName() string {
	if e.cur == nil {
		return ""
	}
	return e.cur.Name
}

// CurrentID returns the current animation id.
func (e *Engine) CurrentID() int {
	if e.cur == nil {
		return -1
	}
	return e.cur.ID
}

// Spawn places the pet according to a random spawn entry and starts its animation.
func (e *Engine) Spawn() {
	sum := 0
	for _, s := range e.P.Spawns {
		sum += s.Prob
	}
	pick := &e.P.Spawns[0]
	if sum > 0 {
		v := e.Rng.Intn(sum) + 1
		acc := 0
		for i := range e.P.Spawns {
			acc += e.P.Spawns[i].Prob
			if acc >= v {
				pick = &e.P.Spawns[i]
				break
			}
		}
	}
	e.X = e.eval(pick.X)
	e.Y = e.eval(pick.Y)
	e.Flipped = false
	e.clamp()
	e.start(pick.Next)
}

func (e *Engine) start(id int) {
	a, ok := e.P.Anims[id]
	if !ok {
		a = e.P.Anims[e.P.Spawns[0].Next]
	}
	e.cur, e.step = a, 0
	e.sx, e.sy = e.eval(a.Start.X)*e.Scale, e.eval(a.Start.Y)*e.Scale
	e.ex, e.ey = e.eval(a.End.X)*e.Scale, e.eval(a.End.Y)*e.Scale
	e.sint, e.eint = int(e.eval(a.Start.Interval)), int(e.eval(a.End.Interval))
	rep := 0
	if strings.TrimSpace(a.Repeat) != "" {
		rep = int(e.eval(a.Repeat))
	}
	if rep < 0 {
		rep = 0
	}
	n := len(a.Frames)
	rf := a.RepeatFrom
	if rf < 0 || rf >= n {
		rf = 0
	}
	e.total = n + (n-rf)*rep
	if e.total < 1 {
		e.total = 1
	}
	e.Interval = clampInterval(e.sint)
}

func clampInterval(v int) int {
	if v < 15 {
		return 15
	}
	return v
}

func onlyMatches(only, where string) bool {
	only = strings.TrimSpace(only)
	if only == "" || only == "none" {
		return true
	}
	horiz := where == "horizontal" || where == "window" || where == "taskbar"
	vert := where == "vertical" || where == "windowSide" || where == "petSide"
	return only == where || (horiz && only == "horizontal+") || (vert && only == "vertical+")
}

// pick chooses one entry from list by relative probability. -1 if none.
func (e *Engine) pick(list []Next, where string) int {
	sum := 0
	for _, n := range list {
		if onlyMatches(n.Only, where) {
			sum += n.Prob
		}
	}
	if sum <= 0 {
		return -1
	}
	v := e.Rng.Intn(sum) + 1
	acc := 0
	for _, n := range list {
		if !onlyMatches(n.Only, where) {
			continue
		}
		acc += n.Prob
		if acc >= v {
			return n.ID
		}
	}
	return -1
}

func (e *Engine) frameAt(step int) int {
	a := e.cur
	n := len(a.Frames)
	rf := a.RepeatFrom
	if rf < 0 || rf >= n {
		rf = 0
	}
	if step < n {
		return a.Frames[step]
	}
	return a.Frames[(step-n+rf)%(n-rf)+rf]
}

// BeginDrag switches to the drag animation (if the pet has one).
func (e *Engine) BeginDrag() {
	e.Dragging = true
	if e.P.DragID != 0 {
		e.start(e.P.DragID)
	}
}

// DragTo moves the pet while dragging; (x, y) is the desired top-left.
func (e *Engine) DragTo(x, y float64) {
	e.X, e.Y = x, y
	e.X = math.Max(0, math.Min(e.X, e.Env.AreaW-e.imageW()))
	e.Y = math.Max(0, math.Min(e.Y, e.Env.AreaH-e.imageH()))
}

// EndDrag releases the pet: it falls if it is in the air.
func (e *Engine) EndDrag() {
	e.Dragging = false
	if e.Y < e.floorY()-1 && e.P.FallID != 0 {
		e.start(e.P.FallID)
		return
	}
	e.Y = math.Min(e.Y, e.floorY())
	e.newNext(e.cur, "none")
}

func (e *Engine) newNext(a *Anim, where string) {
	id := -1
	if a != nil {
		id = e.pick(a.Next, where)
	}
	if id < 0 {
		e.Spawn()
		return
	}
	e.start(id)
}

// Tick advances one animation step. After it returns, the Output fields
// describe what to draw and Interval is the delay before the next Tick.
func (e *Engine) Tick() {
	if e.cur == nil {
		e.Spawn()
	}
	a := e.cur
	e.Frame = e.frameAt(minInt(e.step, e.total-1))
	prog := 0.0
	if e.total > 0 {
		prog = float64(e.step) / float64(e.total)
	}
	e.Interval = clampInterval(e.sint + int(float64(e.eint-e.sint)*prog))
	e.Opacity = a.Start.Opacity + (a.End.Opacity-a.Start.Opacity)*prog
	e.OffsetY = (a.Start.OffsetY + (a.End.OffsetY-a.Start.OffsetY)*prog) * e.Scale

	if e.Dragging {
		e.step++
		if e.step >= e.total {
			e.step = 0
		}
		return
	}

	x, y := e.sx, e.sy
	if e.total > 1 {
		f := float64(e.step) / float64(e.total-1)
		x += (e.ex - e.sx) * f
		y += (e.ey - e.sy) * f
	}
	if e.Flipped {
		x = -x
	}

	w, h := e.imageW(), e.imageH()
	floor := e.floorY()

	// Border detection: screen edges.
	hit := func(where string) bool {
		id := e.pick(a.Border, where)
		if id < 0 {
			return false
		}
		e.start(id)
		return true
	}
	if x < 0 && e.X+x < 0 {
		e.X = 0
		if hit("vertical") {
			return
		}
		x = 0
	} else if x > 0 && e.X+w+x > e.Env.AreaW {
		e.X = e.Env.AreaW - w
		if hit("vertical") {
			return
		}
		x = 0
	}
	if y < 0 && e.Y+y < 0 {
		e.Y = 0
		if hit("horizontal") {
			return
		}
		y = 0
	} else if y > 0 && e.Y+y > floor {
		e.Y = floor
		if hit("taskbar") {
			return
		}
		y = 0
	}
	e.X += x
	e.Y += y
	e.step++

	// Gravity: airborne pets switch to a gravity animation when one is defined.
	if len(a.Grav) > 0 && e.Y < floor-1 {
		if id := e.pick(a.Grav, "none"); id >= 0 {
			e.start(id)
			return
		}
	}
	_ = h

	if e.step >= e.total {
		if a.Flip {
			e.Flipped = !e.Flipped
		}
		e.newNext(a, "none")
	}
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
