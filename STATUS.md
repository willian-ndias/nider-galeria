# Nider Galeria — status 22/10/2026

Pasta local: `d:\nider-galeria`

GitHub: repositório privado `willian-ndias/nider-galeria`. Um programa só, então um repo só. O Nider Forte é a aba do cofre dentro do Nider Galeria, não um segundo projeto.

Este projeto não fica dentro do repositório do Nider Terminal. Text, PDF e Etapas continuam cada um no próprio repositório.

## Nomes

- App na tela inicial: **Nider Galeria**
- Aba do cofre: **Nider Forte**
- Pacote: `br.com.nider.biblioteca`
- Textos da tela: português do Brasil

## O que o código já faz

- Fotos ficam dentro do app, fora da galeria do celular, em pastas.
- Foto abre com zoom, giro, troca para o lado e fecha arrastando para baixo.
- No primeiro uso cadastram-se duas senhas. A primeira abre a pasta de verdade. A segunda abre a pasta falsa, com a mesma tela. Senha errada não abre nenhuma.
- Quem escolhe o que entra em cada pasta é o dono do celular. A pasta falsa começa vazia.
- Foto e PDF abrem no app. Outro arquivo abre no aplicativo do celular.
- Cada pasta do cofre é criptografada com a senha dela. No disco as pastas não se chamam “verdadeira” nem “falsa”.
- Print da tela do app fica bloqueado.
- O cofre não se tranca sozinho no meio de escolher um arquivo.
- Arquivo aberto em outro aplicativo continua disponível até você voltar ao Nider Galeria. Aí a cópia temporária é apagada.
- Uma foto que não abre não derruba a pasta.

## Ícones (23/10/2026)

Escolhidos pelo dev na família dos ícones Nider (fundo grafite, desenho branco, logo Cruzeiro do Sul no canto na cor do app):

- **Nider Galeria**: fotos empilhadas, azul `#4F8CF7` (azul, não o ciano do Nider Text). É o ícone do app.
- **Nider Forte**: porta de cofre, cinza prateado `#A9B3BF` (maçaneta `#5C6672`). É o ícone da aba do cofre.
- **Nider Music**: fone de ouvido, verde `#2FD08A`. Guardado para o app de música, que ainda não existe.

Os originais em SVG ficam em `arte/icones/`. `python3 arte/icones/gerar.py` (precisa do `resvg`) gera os PNG do Android no tamanho exato de cada densidade, sem reescala:

- ícone adaptativo: `mipmap-*/ic_launcher_foreground.png` sobre o fundo vetorial `drawable/ic_bg.xml`, mais `ic_launcher_monochrome.png` para o ícone temático do Android 13;
- abas Fotos e Nider Forte: `drawable-*/ic_aba_fotos.png` e `ic_aba_forte.png` (24 dp, coloridos), usados na barra de baixo (`Ui.kt`).

Falta conferir no celular, com o APK instalado, como o ícone fica em cada formato de máscara (círculo, quadrado arredondado).

## O que falta

- Gerar o APK e instalar no celular. Nesta máquina não tem Android Studio.
- Miniatura crescendo até a tela e filtro de foto.

## Amanhã

Abrir `d:\nider-galeria` no Android Studio, deixar o Gradle baixar, instalar no celular e corrigir o que aparecer na tela.

Música não entra neste projeto. Depois, app separado, a partir do player MIT [PixelPlay-vanilla](https://github.com/dhaneshdutta/PixelPlay-vanilla).
