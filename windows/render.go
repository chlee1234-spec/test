package main

import (
	"fmt"
	"image"
	"image/draw"
	"image/png"
	"io"
	"math"
)

// Sheet is a sprite sheet converted to premultiplied BGRA (what Win32 layered
// windows expect) and scaled so that one cell is CellW x CellH pixels.
type Sheet struct {
	CellW, CellH int
	TilesX       int
	TilesY       int
	W, H         int
	Pix          []byte // premultiplied BGRA, W*H*4
}

// LoadSheet decodes sprite.png and scales every cell to (dw, dh).
func LoadSheet(r io.Reader, tilesX, tilesY, dw, dh int) (*Sheet, error) {
	img, err := png.Decode(r)
	if err != nil {
		return nil, err
	}
	b := img.Bounds()
	src := image.NewNRGBA(image.Rect(0, 0, b.Dx(), b.Dy()))
	draw.Draw(src, src.Bounds(), img, b.Min, draw.Src)
	if b.Dx()%tilesX != 0 || b.Dy()%tilesY != 0 {
		return nil, fmt.Errorf("sprite %dx%d not divisible by %dx%d tiles", b.Dx(), b.Dy(), tilesX, tilesY)
	}
	sw, sh := b.Dx()/tilesX, b.Dy()/tilesY
	s := &Sheet{CellW: dw, CellH: dh, TilesX: tilesX, TilesY: tilesY, W: dw * tilesX, H: dh * tilesY}
	s.Pix = make([]byte, s.W*s.H*4)
	for ty := 0; ty < tilesY; ty++ {
		for tx := 0; tx < tilesX; tx++ {
			for y := 0; y < dh; y++ {
				for x := 0; x < dw; x++ {
					r, g, bl, a := sample(src, tx*sw, ty*sh, sw, sh, (float64(x)+0.5)*float64(sw)/float64(dw)-0.5, (float64(y)+0.5)*float64(sh)/float64(dh)-0.5)
					o := ((ty*dh+y)*s.W + tx*dw + x) * 4
					// premultiply
					s.Pix[o+0] = byte(math.Round(bl * a))
					s.Pix[o+1] = byte(math.Round(g * a))
					s.Pix[o+2] = byte(math.Round(r * a))
					s.Pix[o+3] = byte(math.Round(a * 255))
				}
			}
		}
	}
	return s, nil
}

// sample does a bilinear lookup inside one source cell; colours are weighted by
// alpha so transparent pixels do not bleed their colour into edges.
// Returns r,g,b in 0..255 (non-premultiplied) and a in 0..1.
func sample(src *image.NRGBA, ox, oy, w, h int, fx, fy float64) (r, g, b, a float64) {
	x0, y0 := int(math.Floor(fx)), int(math.Floor(fy))
	tx, ty := fx-float64(x0), fy-float64(y0)
	var sr, sg, sb, sa float64
	for j := 0; j < 2; j++ {
		for i := 0; i < 2; i++ {
			x, y := clampInt(x0+i, 0, w-1), clampInt(y0+j, 0, h-1)
			wt := (1 - tx + float64(i)*(2*tx-1)) * (1 - ty + float64(j)*(2*ty-1))
			p := src.NRGBAAt(ox+x, oy+y)
			pa := float64(p.A) / 255
			sa += wt * pa
			sr += wt * pa * float64(p.R)
			sg += wt * pa * float64(p.G)
			sb += wt * pa * float64(p.B)
		}
	}
	if sa <= 0 {
		return 0, 0, 0, 0
	}
	return sr / sa, sg / sa, sb / sa, sa
}

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

// Blit copies one frame (row-major index) into dst (CellW*CellH*4), optionally mirrored.
func (s *Sheet) Blit(dst []byte, frame int, flip bool) {
	if frame < 0 || frame >= s.TilesX*s.TilesY {
		for i := range dst {
			dst[i] = 0
		}
		return
	}
	fx, fy := frame%s.TilesX, frame/s.TilesX
	for y := 0; y < s.CellH; y++ {
		row := ((fy*s.CellH+y)*s.W + fx*s.CellW) * 4
		out := y * s.CellW * 4
		if !flip {
			copy(dst[out:out+s.CellW*4], s.Pix[row:row+s.CellW*4])
			continue
		}
		for x := 0; x < s.CellW; x++ {
			copy(dst[out+x*4:out+x*4+4], s.Pix[row+(s.CellW-1-x)*4:row+(s.CellW-1-x)*4+4])
		}
	}
}
