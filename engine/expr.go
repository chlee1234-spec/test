package engine

import (
	"fmt"
	"math"
	"strconv"
	"strings"
)

// Vars are the keywords an expression may use (see animations.xsd expressionType).
type Vars struct {
	ScreenW, ScreenH, AreaW, AreaH float64
	ImageW, ImageH                 float64
	Random, RandS, Scale           float64 // Random is drawn per evaluation by the caller
}

type tokenKind int

const (
	tNum tokenKind = iota
	tIdent
	tOp
	tEOF
)

type token struct {
	kind tokenKind
	s    string
	n    float64
}

func lex(src string) ([]token, error) {
	var out []token
	i := 0
	for i < len(src) {
		c := src[i]
		switch {
		case c == ' ' || c == '\t' || c == '\r' || c == '\n':
			i++
		case c >= '0' && c <= '9' || c == '.':
			j := i
			for j < len(src) && (src[j] >= '0' && src[j] <= '9' || src[j] == '.') {
				j++
			}
			n, err := strconv.ParseFloat(src[i:j], 64)
			if err != nil {
				return nil, err
			}
			out = append(out, token{kind: tNum, n: n})
			i = j
		case c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_':
			j := i
			for j < len(src) && (src[j] >= 'A' && src[j] <= 'Z' || src[j] >= 'a' && src[j] <= 'z' ||
				src[j] >= '0' && src[j] <= '9' || src[j] == '_' || src[j] == '.') {
				j++
			}
			out = append(out, token{kind: tIdent, s: strings.ToLower(src[i:j])})
			i = j
		case c == '<' || c == '>':
			if i+1 < len(src) && (src[i+1] == '=' || (c == '<' && src[i+1] == '>')) {
				out = append(out, token{kind: tOp, s: src[i : i+2]})
				i += 2
			} else {
				out = append(out, token{kind: tOp, s: string(c)})
				i++
			}
		case strings.ContainsRune("+-*/%(),=", rune(c)):
			out = append(out, token{kind: tOp, s: string(c)})
			i++
		default:
			return nil, fmt.Errorf("bad character %q in expression %q", c, src)
		}
	}
	return append(out, token{kind: tEOF}), nil
}

type parser struct {
	t []token
	p int
	v Vars
}

func (p *parser) peek() token { return p.t[p.p] }
func (p *parser) next() token { t := p.t[p.p]; p.p++; return t }
func (p *parser) isOp(s string) bool {
	t := p.peek()
	return t.kind == tOp && t.s == s
}
func (p *parser) isWord(s string) bool {
	t := p.peek()
	return t.kind == tIdent && t.s == s
}

func b2f(b bool) float64 {
	if b {
		return 1
	}
	return 0
}

func (p *parser) or() (float64, error) {
	l, err := p.and()
	for err == nil && p.isWord("or") {
		p.next()
		var r float64
		if r, err = p.and(); err == nil {
			l = b2f(l != 0 || r != 0)
		}
	}
	return l, err
}

func (p *parser) and() (float64, error) {
	l, err := p.not()
	for err == nil && p.isWord("and") {
		p.next()
		var r float64
		if r, err = p.not(); err == nil {
			l = b2f(l != 0 && r != 0)
		}
	}
	return l, err
}

func (p *parser) not() (float64, error) {
	if p.isWord("not") {
		p.next()
		v, err := p.not()
		return b2f(v == 0), err
	}
	return p.cmp()
}

func (p *parser) cmp() (float64, error) {
	l, err := p.add()
	if err != nil {
		return 0, err
	}
	for p.peek().kind == tOp {
		op := p.peek().s
		if op != "<" && op != ">" && op != "<=" && op != ">=" && op != "=" && op != "<>" {
			break
		}
		p.next()
		r, err := p.add()
		if err != nil {
			return 0, err
		}
		switch op {
		case "<":
			l = b2f(l < r)
		case ">":
			l = b2f(l > r)
		case "<=":
			l = b2f(l <= r)
		case ">=":
			l = b2f(l >= r)
		case "=":
			l = b2f(l == r)
		case "<>":
			l = b2f(l != r)
		}
	}
	return l, nil
}

func (p *parser) add() (float64, error) {
	l, err := p.mul()
	for err == nil && (p.isOp("+") || p.isOp("-")) {
		op := p.next().s
		var r float64
		if r, err = p.mul(); err == nil {
			if op == "+" {
				l += r
			} else {
				l -= r
			}
		}
	}
	return l, err
}

func (p *parser) mul() (float64, error) {
	l, err := p.unary()
	for err == nil && (p.isOp("*") || p.isOp("/") || p.isOp("%")) {
		op := p.next().s
		var r float64
		if r, err = p.unary(); err != nil {
			break
		}
		switch op {
		case "*":
			l *= r
		case "/":
			if r == 0 {
				l = 0
			} else {
				l /= r
			}
		case "%":
			if r == 0 {
				l = 0
			} else {
				l = math.Mod(l, r)
			}
		}
	}
	return l, err
}

func (p *parser) unary() (float64, error) {
	if p.isOp("-") {
		p.next()
		v, err := p.unary()
		return -v, err
	}
	if p.isOp("+") {
		p.next()
		return p.unary()
	}
	return p.primary()
}

func (p *parser) primary() (float64, error) {
	t := p.next()
	switch t.kind {
	case tNum:
		return t.n, nil
	case tOp:
		if t.s == "(" {
			v, err := p.or()
			if err != nil {
				return 0, err
			}
			if !p.isOp(")") {
				return 0, fmt.Errorf("expected )")
			}
			p.next()
			return v, nil
		}
	case tIdent:
		switch t.s {
		case "screenw":
			return p.v.ScreenW, nil
		case "screenh":
			return p.v.ScreenH, nil
		case "areaw":
			return p.v.AreaW, nil
		case "areah":
			return p.v.AreaH, nil
		case "imagew":
			return p.v.ImageW, nil
		case "imageh":
			return p.v.ImageH, nil
		case "imagex", "imagey":
			return -1, nil // only meaningful for child pets (unsupported)
		case "random":
			return p.v.Random, nil
		case "rands":
			return p.v.RandS, nil
		case "scale":
			return p.v.Scale, nil
		case "true":
			return 1, nil
		case "false":
			return 0, nil
		case "iif":
			args, err := p.args()
			if err != nil || len(args) != 3 {
				return 0, fmt.Errorf("iif needs 3 arguments")
			}
			if args[0] != 0 {
				return args[1], nil
			}
			return args[2], nil
		case "convert":
			if !p.isOp("(") {
				return 0, fmt.Errorf("Convert needs (")
			}
			p.next()
			v, err := p.or()
			if err != nil {
				return 0, err
			}
			typ := ""
			if p.isOp(",") {
				p.next()
				typ = p.next().s
			}
			if !p.isOp(")") {
				return 0, fmt.Errorf("expected ) after Convert")
			}
			p.next()
			if strings.Contains(typ, "int") || strings.Contains(typ, "byte") {
				return math.Trunc(v), nil
			}
			return v, nil
		}
	}
	return 0, fmt.Errorf("unexpected token %q", t.s)
}

func (p *parser) args() ([]float64, error) {
	if !p.isOp("(") {
		return nil, fmt.Errorf("expected (")
	}
	p.next()
	var out []float64
	for {
		v, err := p.or()
		if err != nil {
			return nil, err
		}
		out = append(out, v)
		if p.isOp(",") {
			p.next()
			continue
		}
		break
	}
	if !p.isOp(")") {
		return nil, fmt.Errorf("expected )")
	}
	p.next()
	return out, nil
}

// Eval evaluates an expression and returns the value truncated to an integer
// (like the original application).
func Eval(src string, v Vars) (int, error) {
	f, err := EvalFloat(src, v)
	return int(math.Trunc(f)), err
}

// EvalFloat evaluates an expression without truncating.
func EvalFloat(src string, v Vars) (float64, error) {
	toks, err := lex(src)
	if err != nil {
		return 0, err
	}
	p := &parser{t: toks, v: v}
	r, err := p.or()
	if err != nil {
		return 0, err
	}
	if p.peek().kind != tEOF {
		return 0, fmt.Errorf("trailing input in %q", src)
	}
	return r, nil
}
