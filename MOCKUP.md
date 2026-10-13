# AndrosBrowser — atração "Civic Adventure"

Fork do [AABrowser](https://github.com/kododake/AABrowser) via
[AABrowser-in-motion](https://github.com/rafzei/AABrowser-in-motion), ajustado para
um carrinho de montanha-russa com carroceria de Civic 10ª geração e a central de fábrica
(Display Audio, 7"). O carrinho **anda no trilho, mas não tem motorista** — o vídeo é para os passageiros.

## O que muda em relação ao upstream

- Bypass de restrição de movimento **ligado por padrão** (desliga em Configurações > Experimental).
- Página inicial: `https://m.youtube.com`.
- Tela sempre ligada enquanto o app está aberto.
- Telemetria Umami removida (o app não manda nada para servidor de terceiros).
- GitHub Actions gera o APK debug a cada push (aba Actions > artifact `AndrosBrowser-debug-apk`).

## Arquitetura

```
Celular Android 15+  --USB-->  Display Audio do Civic  (Android Auto)
  (AndrosBrowser)                (só projeta a tela)
```

- O APK roda **no celular**, não na central. `minSdk = 35` → o celular precisa ter Android 15+.
- A central do Civic 10ª geração roda um Android antigo com whitelist de apps da Honda;
  a WebView dela não roda o YouTube atual. Instalar direto nela não é o caminho.

## Limite do bypass em software

O toggle do app controla só a lógica do próprio app. Quem decide se o Android Auto
bloqueia a tela é o **app Android Auto do celular**, a partir do estado de condução
que a **central** reporta (velocidade/freio de mão via B-CAN) e, possivelmente, do GPS
do celular. Sem o chicote de um carro real esses sinais não existem ou vêm inconsistentes —
o comportamento precisa ser validado com a central real, de preferência com o carrinho rodando.

## Instalação

### Caminho A — installer = Play Store (recomendado, técnica do AAAD)

O [AAAD](https://github.com/shmykelsa/AAAD) faz apps de terceiros aparecerem no Android Auto
sem "Fontes desconhecidas" instalando o APK com a **Play Store registrada como installer**
(`com.android.vending`). O Android Auto usa essa origem para decidir se o app é confiável.
O AAAD faz isso no próprio celular (Shizuku no Android 14+); aqui, como o celular da atração
é um só e fica sob nosso controle, fazemos o mesmo via ADB a partir de um PC.

1. Baixar o APK do último run "Build APK" na aba Actions.
2. No celular: Opções do desenvolvedor > **Depuração USB** ligada; conectar no PC.
3. Rodar `scripts/install-as-play-store.sh <apk>` (ou `.ps1` no Windows).
4. Conferir em Configurações > Experimental > **Install source** = `com.android.vending` (verde).
5. Conectar o celular na central via USB e abrir o AndrosBrowser no Android Auto.

Toda atualização do APK precisa passar pelo script de novo — instalar pelo gerenciador de
arquivos troca o installer e o app volta a depender do Caminho B.

### Caminho B — Fontes desconhecidas (fallback)

1. Instalar o APK normalmente no celular.
2. No celular: Android Auto > tocar 10x em "Versão" > menu ⋮ > Configurações do desenvolvedor > **Fontes desconhecidas**.
3. Conectar o celular na central via USB e abrir o AndrosBrowser no Android Auto.

## Checklist físico da atração

- Central alimentada com 12 V estável: +B (constante), ACC e GND.
- Celular e cabo USB travados mecanicamente — nada solto no carrinho; cabo sem tensão
  (desconexão do USB derruba o Android Auto).
- Conectividade: YouTube depende de 4G estável no trajeto. Se oscilar, considerar vídeo local em loop.
