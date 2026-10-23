"""Gera os ícones do Android a partir dos SVGs desta pasta (precisa do `resvg` no PATH).

    python3 arte/icones/gerar.py

- Ícone adaptativo do Nider Galeria: primeiro plano (logo no canto + fotos) em PNG por densidade,
  renderizado no tamanho exato de cada uma (sem reescala, sem serrilhado). O fundo grafite é o
  vetor `drawable/ic_bg.xml`.
- Ícone temático (Android 13+): só o desenho das fotos, em branco.
- Abas Fotos e Nider Forte: o desenho de cada app, colorido, 24 dp.
"""
import pathlib, re, subprocess, tempfile

AQUI = pathlib.Path(__file__).resolve().parent
RES = AQUI.parent.parent / "app/src/main/res"
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

def partes(svg: str):
    logo = re.search(r'<g clip-path="url\(#c\)">.*?</g></g>', svg).group(0)
    desenho = re.search(r'<g transform="translate\(16,16\) scale\(\.8\)".*?</g></svg>', svg).group(0)[: -len("</svg>")]
    return logo, desenho

def render(svg: str, px: int, out: pathlib.Path):
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    subprocess.run(["resvg", "-w", str(px), "-h", str(px), f.name, str(out)], check=True)

def adaptativo(nome: str):
    logo, desenho = partes((AQUI / f"{nome}.svg").read_text())
    # A arte é um quadro de 256 com o cartão em 8..248; no ícone adaptativo (108 dp) a parte visível
    # é 18..90, então o cartão vai para esse quadrado.
    k = 72 / 240
    fg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>'
        '<clipPath id="c"><rect x="8" y="8" width="240" height="240"/></clipPath></defs>'
        f'<g transform="translate({18 - 8 * k:.3f},{18 - 8 * k:.3f}) scale({k:.5f})">{logo}{desenho}</g></svg>'
    )
    mono = re.sub(r'fill="#[0-9A-Fa-f]{6}"', 'fill="#FFFFFF"', desenho)
    mono = re.sub(r'stroke="#[0-9A-Fa-f]{6}"', 'stroke="#FFFFFF"', mono)
    mono_svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
        f'<g transform="translate(20,20) scale(0.36)">{mono}</g></svg>'
    )
    for d, s in DENS.items():
        render(fg, round(108 * s), RES / f"mipmap-{d}/ic_launcher_foreground.png")
        render(mono_svg, round(108 * s), RES / f"mipmap-{d}/ic_launcher_monochrome.png")

def aba(nome: str, arquivo: str):
    _, desenho = partes((AQUI / f"{nome}.svg").read_text())
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="30 28 216 216">{desenho}</svg>'
    for d, s in DENS.items():
        render(svg, round(24 * s), RES / f"drawable-{d}/{arquivo}.png")

adaptativo("nider-galeria")
aba("nider-galeria", "ic_aba_fotos")
aba("nider-forte", "ic_aba_forte")
