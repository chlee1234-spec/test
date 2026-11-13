package main

// Win32 structures (kept in a file without build tags so their sizes can be
// unit-tested on any OS).

type winPOINT struct{ X, Y int32 }
type winSIZE struct{ CX, CY int32 }
type winRECT struct{ Left, Top, Right, Bottom int32 }

type winMSG struct {
	HWND    uintptr
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	Pt      winPOINT
}

type winWNDCLASSEX struct {
	Size       uint32
	Style      uint32
	WndProc    uintptr
	ClsExtra   int32
	WndExtra   int32
	Instance   uintptr
	Icon       uintptr
	Cursor     uintptr
	Background uintptr
	MenuName   *uint16
	ClassName  *uint16
	IconSm     uintptr
}

type winBITMAPINFOHEADER struct {
	Size          uint32
	Width         int32
	Height        int32
	Planes        uint16
	BitCount      uint16
	Compression   uint32
	SizeImage     uint32
	XPelsPerMeter int32
	YPelsPerMeter int32
	ClrUsed       uint32
	ClrImportant  uint32
}

type winBLEND struct {
	Op, Flags, Alpha, Format byte
}
