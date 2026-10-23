# Nider Galeria — status 22/10/2026

Pasta local: `d:\nider-terminal\nider-biblioteca`

GitHub: repositório privado `willian-ndias/nider-galeria`. Um programa só, então um repo só. O Nider Forte é a aba do cofre dentro do Nider Galeria, não um segundo projeto.

Não commitar a pasta pai `d:\nider-terminal`. Ela não é o repositório do Nider. Os apps (nider, nider-etapas, nider-text, nider-pdf) têm o git deles, cada um na própria pasta.

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

## O que falta

- Gerar o APK e instalar no celular. Nesta máquina não tem Android Studio.
- Ícone definitivo. O atual é provisório.
- Miniatura crescendo até a tela e filtro de foto.

## Amanhã

Abrir `d:\nider-terminal\nider-biblioteca` no Android Studio, deixar o Gradle baixar, instalar no celular e corrigir o que aparecer na tela.

Música não entra neste projeto. Depois, app separado, a partir do player MIT [PixelPlay-vanilla](https://github.com/dhaneshdutta/PixelPlay-vanilla).
