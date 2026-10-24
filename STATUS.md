# Nider Galeria — status 24/10/2026

## 2.1.0 beta (Capacitor) — branch `feat/capacitor-2.1`

O app passou de Kotlin/Compose para **Capacitor** (HTML/JS em `src/`, Android em `android/`). O código Kotlin do Grok ficou guardado em `legado-kotlin/`.

- Pacote `br.com.nider.galeria`, versão `2.1.0-beta` (versionCode 210).
- Forte com duas senhas: cada espaço tem uma chave de dados aleatória (AES-256-GCM); a senha (PBKDF2-SHA256, 310 mil voltas) só embrulha essa chave. O principal guarda a chave do disfarce, por isso troca ou apaga a senha de disfarce. Nada no disco diz qual espaço é qual.
- Disfarce com configurações falsas (sem o cartão da senha de disfarce). "Apagar após 10 erros" apaga o Forte inteiro.
- Fotos com miniatura criptografada à parte; visualizador PhotoSwipe (pinça, toque duplo, arrastar); PDF com pdf.js.
- Tranca ao sair do app; print bloqueado dentro do Forte (privacy-screen).
- Galeria e barra de baixo ainda são só o esqueleto ("em breve").

**Até onde foi:** PUSH da branch `feat/capacitor-2.1`. APK de teste (assinatura debug da sessão) entregue ao dev por artefato.
**Falta:** chave de assinatura definitiva + APK pelo GitHub Actions, atualização pelo app, "Compartilhar → Nider Galeria" com apagar a original, "Devolver à galeria", card no Nider Downloads.

Montar: `npm ci && npm run sync && cd android && ./gradlew assembleDebug` (precisa de `ANDROID_HOME`).

---

# Histórico — status 22/10/2026

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
