# Phase 1 Report — LaTeX source

Persian (RTL) document, typeset with **XeLaTeX + xepersian**.

## Requirements

- A TeX distribution with **XeLaTeX** (TeX Live or MacTeX).
- Packages `xepersian` and `bidi`.
- The font **Times New Roman** installed on the system.

## Build

Run **twice** (the second pass builds the table of contents):

```bash
xelatex phase1-report.tex
xelatex phase1-report.tex
```

Must be **XeLaTeX** — not pdfLaTeX or LuaLaTeX.
