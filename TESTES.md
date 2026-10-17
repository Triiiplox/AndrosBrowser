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
- [ ] Depois de instalar por cima: tema Moderno, trilho à esquerda, YouTube como 1º item e abrindo sozinho (os ajustes antigos são reaplicados uma vez).

## 1. Navegação (o "voltando / indo / mudando")

- [ ] Trilho: tocar em cada item (Pastas, Favoritos, Playlists, TV, YouTube, Menu) e voltar — 3x seguidas.
- [ ] Pastas: entrar 3 níveis, voltar até a raiz pelo botão voltar da toolbar.
- [ ] Trocar de seção com um vídeo tocando e voltar para ele.
- [ ] Abrir Configurações, entrar em 2 sub-páginas, voltar.
- [ ] Sair do Fermata para o Waze/tela inicial do AA e voltar — repetir com vídeo tocando.
- [ ] Desconectar e reconectar o USB com o Fermata aberto.

- [ ] Vídeo do YouTube em tela cheia no carro → ir para TV/Pastas: o trilho lateral aparece (não some).
- [ ] Na raiz de TV ou Pastas, apertar voltar (tela ou botão BACK do carro): vai para o YouTube.

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
- [ ] Tocar num vídeo do feed: começa a tocar sozinho (sem dar play).
- [ ] O vídeo começa **com som** (sem "Toque para ativar o som"), no celular e no carro.
- [ ] Vídeo acaba: passa sozinho para o próximo (com a reprodução automática do YouTube ligada ou não).
- [ ] Fechar o app com vídeo tocando (sair do AA / desligar o carro) e abrir de novo: volta no mesmo vídeo, perto de onde parou, tocando.
- [ ] Voltar para o feed e fechar: ao abrir, fica no feed (não retoma).
- [ ] Capa do vídeo do YouTube no painel do Fermata e no card de mídia do AA (miniatura, não o H).
- [ ] Desligar Configurações → YouTube → Reprodução automática: nada disso acontece sozinho.
- [ ] Abrir o app direto num vídeo (retomada) e apertar voltar: vai para o feed, não fecha o app.
- [ ] No carro, apertar voltar no YouTube e em outras telas: não sai do Fermata.
- [ ] Próximo vídeo: troca em ~1 s (antes ~3 s).
- [ ] No carro, tocar no vídeo que está tocando: abre em tela cheia. Tocar num botão do YouTube (pausar, barra) ou com o vídeo pausado: o YouTube responde, não abre tela cheia.
- [ ] Em tela cheia: voltar sai da tela cheia (vídeo continua); voltar de novo vai para o feed.
- [ ] Mandar o `Fermata.log` desse teste: procurar "Full screen on", "Fermata tap" e "fullscreen refused".

## 4. IPTV

- [ ] Abrir TV pela 1ª vez com internet: aparece "Canais abertos do Brasil (iptv-org)" ao lado da sua lista; abrir 3 canais.
- [ ] Menu do trilho, na TV: "Adicionar lista IPTV (M3U)" e "Gerenciar listas IPTV" → lista → Editar / Excluir (pede confirmação).
- [ ] Adicionar lista: entrar em EPG ou Conexão e apertar voltar: volta para o formulário (não fecha). Voltar no formulário: volta para a TV.
- [ ] Adicionar uma 2ª lista: o formulário abre vazio (não vem com a anterior).
- [ ] Botão **+** na barra de cima da TV (em qualquer tela da TV) abre "Adicionar lista IPTV"; o item também está no Menu dentro de listas e grupos.
- [ ] Formulário de lista: tocar na linha do link só abre o teclado; só o ícone de pasta abre o seletor de arquivos. Colar um link http e confirmar: a lista entra.
- [ ] Arrastar uma lista para mudar a ordem, sair e voltar: a ordem fica.
- [ ] Pastas: "Remover pasta" pede confirmação.
- [ ] Importar a playlist M3U (a que dava "sem entradas" deve funcionar — #839).
- [ ] Abrir canal; trocar de canal pela lista e pelo volante.
- [ ] Guia (EPG) aparece, se configurado.
- [ ] Canal fora do ar: mostra erro ou fica preso carregando?
- [ ] TV em cards (sem tocar no botão de grade): listas, grupos e canais em cards largos; o botão de grade some na TV.
- [ ] Logo do canal inteiro, centralizado, sem corte; canal sem logo mostra o emblema.
- [ ] Selo vermelho "AO VIVO" no canto dos canais.
- [ ] Entrar no guia (EPG) de um canal: vira lista; voltar: volta para os cards, na mesma posição.
- [ ] Pastas e Favoritos continuam como estavam (lista ou grade, conforme o botão).

## 5. Vídeo + Waze (atração)

- [ ] Espelhamento do Fermata em paisagem + tela dividida do celular (Waze | YouTube).
- [ ] Toque nas duas metades cai no lugar certo (#796).

## 6. Visual

- [ ] Texto e ícones legíveis de dia e à noite.
- [ ] Botões do painel e do trilho fáceis de acertar.
- [ ] Nada cortado ou sobreposto em 800×480.
- [ ] Ícone novo (emblema H) no celular e no launcher do AA; card de mídia do AA sem capa mostra o emblema.
- [ ] Logo antigo sumiu: ícone da notificação (barra de status e AA) é o H; ícones Espelhar / Início / Voltar no launcher do AA com o fundo escuro novo.
- [ ] Vídeo/áudio sem capa na lista: emblema no card (não o ícone genérico); vídeo com capa continua com a foto.
- [ ] Ao abrir um vídeo/canal: emblema enchendo no centro da tela enquanto carrega, some quando começa.
