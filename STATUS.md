# Nider Galeria — status 24/10/2026

## 2.1.0 beta (Capacitor) — branch `feat/capacitor-2.1`

O app passou de Kotlin/Compose para **Capacitor** (HTML/JS em `src/`, Android em `android/`). O código Kotlin do Grok ficou guardado em `legado-kotlin/`.

- Pacote `br.com.nider.galeria`, versão `2.1.0-beta` (versionCode 210).
- Forte com duas senhas: cada espaço tem uma chave de dados aleatória (AES-256-GCM); a senha (PBKDF2-SHA256, 310 mil voltas) só embrulha essa chave. O principal guarda a chave do disfarce, por isso troca ou apaga a senha de disfarce. Nada no disco diz qual espaço é qual.
- Disfarce com configurações falsas (sem o cartão da senha de disfarce). "Apagar após 10 erros" apaga o Forte inteiro.
- Fotos com miniatura criptografada à parte; visualizador PhotoSwipe (pinça, toque duplo, arrastar); PDF com pdf.js.
- Tranca ao sair do app; print bloqueado dentro do Forte (privacy-screen).
- Galeria e barra de baixo ainda são só o esqueleto ("em breve").
- **Compartilhar → Nider Forte** (fotos e PDFs, um ou vários): guarda **sem senha** e volta para o app de origem (`ForteShareActivity.java`, tela transparente). Cada arquivo vai com uma chave AES nova, trancada pela chave pública RSA-OAEP da caixa de entrada (`config.json`, campo `inbox`); a privada fica só no índice do principal. Ao abrir com a senha principal, o que chegou vai para a pasta **Recebidos**. A original some da galeria: sem pergunta se "Gerenciar mídia" estiver liberado (Configurações do Forte → "Sumir da galeria sem perguntar"), senão com a confirmação do Android. Foto só da nuvem do Google Fotos não tem como ser apagada daqui. A Galeria da Xiaomi manda o caminho do arquivo em vez do endereço do catálogo: a original é procurada no MediaStore por esse caminho (precisa da liberação de ver as fotos). Quando a original não é achada, o aviso diz que ela continua na galeria.
- **Mover** entre pastas do Forte na seleção.
- **Seleção no Forte** (segurar): Todos, Restaurar para a galeria (sai do Forte e vai para `Pictures/Nider Galeria`, PDF para `Download/Nider Galeria`; Android 10+), Renomear, Excluir.
- **Lixeira do Forte**: excluir manda para a lixeira; some de vez após 30 dias (limpa ao abrir o Forte). Apagar pasta manda os itens para a lixeira; recuperar sem a pasta cria "Recuperados".

**Publicar uma versão (desde a 2.1.0 beta oficial):** subir `version` no `package.json` e `versionCode`/`versionName` em `android/app/build.gradle`, escrever `novidades/<versão>.md`, juntar na `main` e rodar o workflow **Publicar versão** (Actions → Run workflow). Ele monta o APK assinado com a chave definitiva e cria a release `v<versão>`; o Nider Downloads lê a release.
- Chave definitiva: `android/nider-galeria.jks.enc` (criptografada; alias `nider-galeria`). A senha fica só no segredo `NIDER_KEYSTORE_PASSWORD` do repositório. **Perder a senha = não dá mais para atualizar por cima** (tem que desinstalar). Os testes 1–6 usaram a chave debug da sessão e precisam ser desinstalados uma vez.

**Até onde foi:** PUSH da branch `feat/capacitor-2.1` + PR para a `main`. Falta o dev criar o segredo `NIDER_KEYSTORE_PASSWORD` e rodar o workflow; depois, card no Nider Downloads (develop → master no repo do Nider).
**Falta:** atualização pelo app (2.2.0). Compartilhar, restaurar e lixeira testados só no navegador (a parte nativa compila, falta o teste no celular).

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
