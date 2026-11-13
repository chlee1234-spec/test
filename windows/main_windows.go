//go:build windows

// Desktop pet for Windows: a transparent, always-on-top, click-through-where-empty
// layered window driven by the shared animation engine. Standard library only.
package main

import (
	"bytes"
	"embed"
	"flag"
	"math"
	"os"
	"runtime"
	"syscall"
	"time"
	"unsafe"

	"petforge/engine"
)

//go:embed assets/pet.txt assets/sprite.png
var assets embed.FS

var (
	user32   = syscall.NewLazyDLL("user32.dll")
	gdi32    = syscall.NewLazyDLL("gdi32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")

	pRegisterClassEx      = user32.NewProc("RegisterClassExW")
	pCreateWindowEx       = user32.NewProc("CreateWindowExW")
	pDefWindowProc        = user32.NewProc("DefWindowProcW")
	pGetMessage           = user32.NewProc("GetMessageW")
	pTranslateMessage     = user32.NewProc("TranslateMessage")
	pDispatchMessage      = user32.NewProc("DispatchMessageW")
	pPostQuitMessage      = user32.NewProc("PostQuitMessage")
	pShowWindow           = user32.NewProc("ShowWindow")
	pSetTimer             = user32.NewProc("SetTimer")
	pKillTimer            = user32.NewProc("KillTimer")
	pGetDC                = user32.NewProc("GetDC")
	pReleaseDC            = user32.NewProc("ReleaseDC")
	pUpdateLayeredWindow  = user32.NewProc("UpdateLayeredWindow")
	pSystemParametersInfo = user32.NewProc("SystemParametersInfoW")
	pGetSystemMetrics     = user32.NewProc("GetSystemMetrics")
	pSetProcessDPIAware   = user32.NewProc("SetProcessDPIAware")
	pLoadCursor           = user32.NewProc("LoadCursorW")
	pSetCapture           = user32.NewProc("SetCapture")
	pReleaseCapture       = user32.NewProc("ReleaseCapture")
	pGetCursorPos         = user32.NewProc("GetCursorPos")
	pCreatePopupMenu      = user32.NewProc("CreatePopupMenu")
	pAppendMenu           = user32.NewProc("AppendMenuW")
	pTrackPopupMenu       = user32.NewProc("TrackPopupMenu")
	pDestroyMenu          = user32.NewProc("DestroyMenu")
	pSetForegroundWindow  = user32.NewProc("SetForegroundWindow")
	pDestroyWindow        = user32.NewProc("DestroyWindow")

	pCreateCompatibleDC = gdi32.NewProc("CreateCompatibleDC")
	pCreateDIBSection   = gdi32.NewProc("CreateDIBSection")
	pSelectObject       = gdi32.NewProc("SelectObject")
	pDeleteObject       = gdi32.NewProc("DeleteObject")
	pDeleteDC           = gdi32.NewProc("DeleteDC")

	pGetModuleHandle = kernel32.NewProc("GetModuleHandleW")
)

const (
	wsPopup          = 0x80000000
	wsExLayered      = 0x00080000
	wsExTopmost      = 0x00000008
	wsExToolWindow   = 0x00000080
	wsExNoActivate   = 0x08000000
	wmDestroy        = 0x0002
	wmMouseActivate  = 0x0021
	wmTimer          = 0x0113
	wmMouseMove      = 0x0200
	wmLButtonDown    = 0x0201
	wmLButtonUp      = 0x0202
	wmRButtonUp      = 0x0205
	swShowNoActivate = 4
	spiGetWorkArea   = 0x0030
	ulwAlpha         = 0x00000002
	acSrcOver        = 0
	acSrcAlpha       = 1
	idcArrow         = 32512
	tpmReturnCmd     = 0x0100
	tpmRightButton   = 0x0002
	timerID          = 1
)

type app struct {
	hwnd     uintptr
	memDC    uintptr
	dib      uintptr
	bits     unsafe.Pointer
	cellW    int
	cellH    int
	sheet    *Sheet
	eng      *engine.Engine
	workLeft int
	workTop  int
	dragging bool
	dragDX   float64
	dragDY   float64
	buf      []byte
}

var a app

func utf16(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }

func screenPoint() (int, int) {
	var pt winPOINT
	pGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))
	return int(pt.X), int(pt.Y)
}

func workArea() (left, top, w, h int) {
	var r winRECT
	ok, _, _ := pSystemParametersInfo.Call(spiGetWorkArea, 0, uintptr(unsafe.Pointer(&r)), 0)
	if ok == 0 {
		sw, _, _ := pGetSystemMetrics.Call(0)
		sh, _, _ := pGetSystemMetrics.Call(1)
		return 0, 0, int(sw), int(sh)
	}
	return int(r.Left), int(r.Top), int(r.Right - r.Left), int(r.Bottom - r.Top)
}

func (p *app) refreshEnv() {
	l, t, w, h := workArea()
	p.workLeft, p.workTop = l, t
	sw, _, _ := pGetSystemMetrics.Call(0)
	sh, _, _ := pGetSystemMetrics.Call(1)
	p.eng.SetEnv(engine.Env{ScreenW: float64(sw), ScreenH: float64(sh), AreaW: float64(w), AreaH: float64(h)})
}

// draw pushes the current frame to the layered window.
func (p *app) draw() {
	e := p.eng
	p.sheet.Blit(p.buf, e.Frame, e.Flipped)
	copy(unsafe.Slice((*byte)(p.bits), len(p.buf)), p.buf)

	x := p.workLeft + int(math.Round(e.X))
	y := p.workTop + int(math.Round(e.Y+e.OffsetY))
	pos := winPOINT{int32(x), int32(y)}
	size := winSIZE{int32(p.cellW), int32(p.cellH)}
	src := winPOINT{0, 0}
	alpha := byte(math.Round(255 * math.Max(0, math.Min(1, e.Opacity))))
	bf := winBLEND{Op: acSrcOver, Alpha: alpha, Format: acSrcAlpha}
	screen, _, _ := pGetDC.Call(0)
	pUpdateLayeredWindow.Call(p.hwnd, screen,
		uintptr(unsafe.Pointer(&pos)), uintptr(unsafe.Pointer(&size)),
		p.memDC, uintptr(unsafe.Pointer(&src)), 0, uintptr(unsafe.Pointer(&bf)), ulwAlpha)
	pReleaseDC.Call(0, screen)
}

func (p *app) tick() {
	p.eng.Tick()
	p.draw()
	pSetTimer.Call(p.hwnd, timerID, uintptr(p.eng.Interval), 0)
}

func loword(v uintptr) int { return int(int16(v & 0xFFFF)) }

func wndProc(hwnd, msg, wparam, lparam uintptr) uintptr {
	switch msg {
	case wmMouseActivate:
		return 3 // MA_NOACTIVATE
	case wmTimer:
		if !a.dragging {
			a.tick()
		}
		return 0
	case wmLButtonDown:
		cx, cy := screenPoint()
		a.dragging = true
		a.dragDX = float64(cx-a.workLeft) - a.eng.X
		a.dragDY = float64(cy-a.workTop) - a.eng.Y
		pSetCapture.Call(hwnd)
		a.eng.BeginDrag()
		a.eng.Tick() // show the drag frame immediately
		a.draw()
		return 0
	case wmMouseMove:
		if a.dragging {
			cx, cy := screenPoint()
			a.eng.DragTo(float64(cx-a.workLeft)-a.dragDX, float64(cy-a.workTop)-a.dragDY)
			a.draw()
		}
		return 0
	case wmLButtonUp:
		if a.dragging {
			a.dragging = false
			pReleaseCapture.Call()
			a.eng.EndDrag()
			a.tick()
		}
		return 0
	case wmRButtonUp:
		showMenu(hwnd)
		return 0
	case wmDestroy:
		pKillTimer.Call(hwnd, timerID)
		pPostQuitMessage.Call(0)
		return 0
	}
	r, _, _ := pDefWindowProc.Call(hwnd, msg, wparam, lparam)
	return r
}

func showMenu(hwnd uintptr) {
	menu, _, _ := pCreatePopupMenu.Call()
	pAppendMenu.Call(menu, 0, 1, uintptr(unsafe.Pointer(utf16("Quit / 結束"))))
	x, y := screenPoint()
	pSetForegroundWindow.Call(hwnd)
	cmd, _, _ := pTrackPopupMenu.Call(menu, tpmReturnCmd|tpmRightButton, uintptr(x), uintptr(y), 0, hwnd, 0)
	pDestroyMenu.Call(menu)
	if cmd == 1 {
		pDestroyWindow.Call(hwnd)
	}
}

func fatal(msg string) {
	os.Stderr.WriteString(msg + "\n")
	os.Exit(1)
}

func main() {
	runtime.LockOSThread() // window + message loop must stay on one thread
	scale := flag.Float64("scale", 1.0, "pet size multiplier")
	flag.Parse()
	pSetProcessDPIAware.Call()

	petTxt, err := assets.ReadFile("assets/pet.txt")
	if err != nil {
		fatal(err.Error())
	}
	pet, err := engine.Parse(bytes.NewReader(petTxt))
	if err != nil {
		fatal("pet.txt: " + err.Error())
	}
	cw := int(math.Round(float64(pet.CellW) * *scale))
	ch := int(math.Round(float64(pet.CellH) * *scale))
	if cw < 8 || ch < 8 {
		fatal("pet too small")
	}
	spr, err := assets.ReadFile("assets/sprite.png")
	if err != nil {
		fatal(err.Error())
	}
	sheet, err := LoadSheet(bytes.NewReader(spr), pet.TilesX, pet.TilesY, cw, ch)
	if err != nil {
		fatal(err.Error())
	}
	a.sheet, a.cellW, a.cellH = sheet, cw, ch
	a.buf = make([]byte, cw*ch*4)
	a.eng = engine.New(pet, engine.Env{}, float64(cw)/float64(pet.CellW), time.Now().UnixNano())

	inst, _, _ := pGetModuleHandle.Call(0)
	cursor, _, _ := pLoadCursor.Call(0, idcArrow)
	cls := utf16("PetForgeWindow")
	wc := winWNDCLASSEX{
		WndProc: syscall.NewCallback(wndProc), Instance: inst, Cursor: cursor, ClassName: cls,
	}
	wc.Size = uint32(unsafe.Sizeof(wc))
	if r, _, e := pRegisterClassEx.Call(uintptr(unsafe.Pointer(&wc))); r == 0 {
		fatal("RegisterClassEx: " + e.Error())
	}
	hwnd, _, e := pCreateWindowEx.Call(
		wsExLayered|wsExTopmost|wsExToolWindow|wsExNoActivate,
		uintptr(unsafe.Pointer(cls)), uintptr(unsafe.Pointer(utf16(pet.Name))),
		wsPopup, 0, 0, uintptr(cw), uintptr(ch), 0, 0, inst, 0)
	if hwnd == 0 {
		fatal("CreateWindowEx: " + e.Error())
	}
	a.hwnd = hwnd

	// 32-bit top-down DIB the frames are rendered into.
	bi := winBITMAPINFOHEADER{Width: int32(cw), Height: -int32(ch), Planes: 1, BitCount: 32}
	bi.Size = uint32(unsafe.Sizeof(bi))
	screen, _, _ := pGetDC.Call(0)
	a.memDC, _, _ = pCreateCompatibleDC.Call(screen)
	a.dib, _, _ = pCreateDIBSection.Call(screen, uintptr(unsafe.Pointer(&bi)), 0, uintptr(unsafe.Pointer(&a.bits)), 0, 0)
	pReleaseDC.Call(0, screen)
	if a.dib == 0 || a.bits == nil {
		fatal("CreateDIBSection failed")
	}
	pSelectObject.Call(a.memDC, a.dib)

	a.refreshEnv()
	a.eng.Spawn()
	pShowWindow.Call(hwnd, swShowNoActivate)
	a.tick()

	var m winMSG
	for {
		r, _, _ := pGetMessage.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(r) <= 0 {
			break
		}
		pTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		pDispatchMessage.Call(uintptr(unsafe.Pointer(&m)))
	}
	pDeleteDC.Call(a.memDC)
	pDeleteObject.Call(a.dib)
}
