package main

import (
	"image"
	"image/color"
	"image/png"
	"os"
	"testing"
	"unsafe"
)

func TestStructSizes(t *testing.T) {
	// Win32 ABI sizes on 64-bit Windows.
	if s := unsafe.Sizeof(winMSG{}); s != 48 {
		t.Errorf("MSG size %d, want 48", s)
	}
	if s := unsafe.Sizeof(winWNDCLASSEX{}); s != 80 {
		t.Errorf("WNDCLASSEXW size %d, want 80", s)
	}
	if s := unsafe.Sizeof(winBITMAPINFOHEADER{}); s != 40 {
		t.Errorf("BITMAPINFOHEADER size %d, want 40", s)
	}
	if s := unsafe.Sizeof(winBLEND{}); s != 4 {
		t.Errorf("BLENDFUNCTION size %d, want 4", s)
	}
}

func TestSheetFlipAndAlpha(t *testing.T) {
	f, err := os.Open("../build/mareep/sprite.png")
	if err != nil {
		t.Skip("run xml2pet first")
	}
	defer f.Close()
	s, err := LoadSheet(f, 4, 4, 96, 96)
	if err != nil {
		t.Fatal(err)
	}
	a := make([]byte, 96*96*4)
	b := make([]byte, 96*96*4)
	s.Blit(a, 0, false)
	s.Blit(b, 0, true)
	opaque := 0
	for i := 3; i < len(a); i += 4 {
		if a[i] > 0 {
			opaque++
		}
		// premultiplied: no channel may exceed alpha
		for c := 0; c < 3; c++ {
			if a[i-3+c] > a[i] {
				t.Fatalf("pixel %d channel %d (%d) exceeds alpha %d", i/4, c, a[i-3+c], a[i])
			}
		}
	}
	if opaque < 500 || opaque > 96*96-500 {
		t.Errorf("suspicious opaque pixel count %d", opaque)
	}
	// mirrored image: pixel (x,y) of a == pixel (w-1-x,y) of b
	for y := 0; y < 96; y += 7 {
		for x := 0; x < 96; x += 5 {
			ia, ib := (y*96+x)*4, (y*96+95-x)*4
			for c := 0; c < 4; c++ {
				if a[ia+c] != b[ib+c] {
					t.Fatalf("flip mismatch at %d,%d", x, y)
				}
			}
		}
	}
	// dump a contact sheet over a checkerboard for visual inspection
	out := image.NewNRGBA(image.Rect(0, 0, 96*4, 96*4))
	buf := make([]byte, 96*96*4)
	for fr := 0; fr < 16; fr++ {
		s.Blit(buf, fr, false)
		for y := 0; y < 96; y++ {
			for x := 0; x < 96; x++ {
				bg := uint8(200)
				if (x/8+y/8)%2 == 0 {
					bg = 235
				}
				i := (y*96 + x) * 4
				al := float64(buf[i+3]) / 255
				blend := func(p byte) uint8 { return uint8(float64(p) + float64(bg)*(1-al)) }
				out.SetNRGBA((fr%4)*96+x, (fr/4)*96+y, color.NRGBA{blend(buf[i+2]), blend(buf[i+1]), blend(buf[i]), 255})
			}
		}
	}
	o, _ := os.Create("../build/mareep/contact.png")
	defer o.Close()
	png.Encode(o, out)
}
