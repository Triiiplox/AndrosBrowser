# Roteiro de teste no carro

Rodar a cada APK novo, na central do Civic (Display Audio 7", AA com fio). Marcar ✅/❌ e, em cada ❌,
anotar o passo e mandar o `Fermata.log` (Configurações → Abrir log) + foto da tela.

Os testes automáticos (job "Unit tests" no CI) cobrem só a lógica sem tela — HTTP, playlists,
legendas, utilitários. Tudo o que envolve tela, toque e AA só se valida aqui.

## 0. Instalação

- [ ] APK arm64 do último build verde da aba Actions instala por cima do anterior, sem desinstalar
      (se pedir para desinstalar, a assinatura mudou — avisar).
- [ ] App aparece no AA (Fontes desconhecidas ligado) e abre sem travar.
- [ ] Tema Moderno ativo por padrão; trilho lateral à esquerda.

## 1. Navegação (o "voltando / indo / mudando")

- [ ] Trilho: tocar em cada item (Pastas, Favoritos, Playlists, TV, YouTube, Menu) e voltar — 3x seguidas.
- [ ] Pastas: entrar 3 níveis, voltar até a raiz pelo botão voltar da toolbar.
- [ ] Trocar de seção com um vídeo tocando e voltar para ele.
- [ ] Abrir Configurações, entrar em 2 sub-páginas, voltar.
- [ ] Sair do Fermata para o Waze/tela inicial do AA e voltar — repetir com vídeo tocando.
- [ ] Desconectar e reconectar o USB com o Fermata aberto.

## 2. Vídeo local

- [ ] Abrir vídeo; play/pause; avançar/voltar; arrastar a barra.
- [ ] Anterior/próximo pela tela e pelos botões do volante.
- [ ] Tela cheia: tocar para mostrar/esconder controles; controles somem sozinhos.
- [ ] Voltar do vídeo para a lista; o item tocando fica destacado e com progresso.
- [ ] Capa: pasta com `cover.jpg`/`folder.jpg` mostra a foto; vídeo mostra frame/capa no card.

## 3. YouTube

- [ ] Abrir vídeo; play/pause pelo painel do Fermata.
- [ ] **Anterior/próximo** pela tela e pelo volante (correção do #841).
- [ ] Voltar dentro do YouTube para o feed: o painel do Fermata para de mostrar o vídeo (#844).
- [ ] Rolar o feed: prévias mudas não tomam o painel (#844).
- [ ] Título no painel = vídeo atual, não o anterior (#844).
- [ ] Sair do AA com o YouTube aberto e voltar: não fica travado.
- [ ] Anúncio: aparece? é pulado (inclusive no meio do vídeo)?
- [ ] App abre direto no YouTube; desligar "Abrir ao iniciar" no YouTube e reabrir: abre na biblioteca.
- [ ] Login do Google (navegador e YouTube): entra sem "navegador não seguro"?

## 4. IPTV

- [ ] Importar a playlist M3U (a que dava "sem entradas" deve funcionar — #839).
- [ ] Abrir canal; trocar de canal pela lista e pelo volante.
- [ ] Guia (EPG) aparece, se configurado.
- [ ] Canal fora do ar: mostra erro ou fica preso carregando?

## 5. Vídeo + Waze (atração)

- [ ] Espelhamento do Fermata em paisagem + tela dividida do celular (Waze | YouTube).
- [ ] Toque nas duas metades cai no lugar certo (#796).

## 6. Visual

- [ ] Texto e ícones legíveis de dia e à noite.
- [ ] Botões do painel e do trilho fáceis de acertar.
- [ ] Nada cortado ou sobreposto em 800×480.
- [ ] Ícone novo (emblema H) no celular e no launcher do AA; card de mídia do AA sem capa mostra o emblema.
- [ ] Vídeo/áudio sem capa na lista: emblema no card (não o ícone genérico); vídeo com capa continua com a foto.
- [ ] Ao abrir um vídeo/canal: emblema enchendo no centro da tela enquanto carrega, some quando começa.
