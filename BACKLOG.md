# Backlog — Fermata Auto (fork do Andro)

Fork do [AndreyPavlenko/Fermata](https://github.com/AndreyPavlenko/Fermata) 2.0.2, usado no
Civic G10 EXL 2020 (Display Audio 7", só toque, AA com fio). Uso principal: vídeo, YouTube,
streaming e IPTV.

Cada entrega: simulação → aprovação → código → build no CI → teste no carro com fotos.

## Feito

| Commit | O quê |
|---|---|
| `7ae779d` | Dependências nativas travadas em commit (whisper.cpp, SPIRV-Headers); auto-update do upstream desligado no fork; validação do gradle-wrapper no CI |
| `d04f0b4` | Assinatura do APK por secret `FERMATA_SIGNING_SEED` (sem ele, a chave padrão é pública) |
| `7986b70` | Tema **Moderno** (padrão): contraste ≥ 4,9:1, toolbar/nav 48dp, painel 84dp, play/pause em destaque |
| `e9c2b45` | PR upstream #841: anterior/próximo do YouTube pela API do player (issues #809, #759) |
| `87339db` | PR upstream #844: sessão de mídia do YouTube em sincronia (voltar, prévias do feed, sair do AA, título) |
| `285fb55` | PR upstream #839: download de playlist IPTV (chunked e close-delimited) |
| `853f308` | Crash de build do fork não manda email para o autor upstream (fica no `Fermata.log`) |
| `45c7b30` | Ícone do app (celular e AA): emblema H + play enviado pelo Andro, fundo escuro do tema; versão monocromática para ícones temáticos |
| `ab6fb76` | Emblema no player: capa padrão de vídeo/áudio sem capa e animação de carregamento (tema Moderno); o card do AA sem capa já usa o ícone do app |
| `44766f4` | YouTube: capa do vídeo (miniatura `i.ytimg.com`) no painel e no AA; reprodução automática ao abrir um vídeo, retomar o último vídeo ao iniciar o app, próximo vídeo no fim mesmo sem o autoplay do YouTube (chave nova, ligada por padrão) |
| — | IPTV/pastas: voltar no formulário de lista volta uma página (antes fechava tudo); "Adicionar" e "Gerenciar listas" (editar/excluir) no menu do trilho; excluir lista e remover pasta pedem confirmação; formulário abre vazio; ordem das listas salva; lista padrão "Canais abertos do Brasil (iptv-org)" — 271/398 canais tocaram no teste do CI (runner nos EUA) |
| — | YouTube: voltar sem histórico (vídeo aberto direto) vai para o feed; no carro o voltar não sai do app (hipótese: o AA saía porque o Back não era marcado como tratado); próximo/anterior sem ida à rede para metadados; fallback do próximo em até 1,2 s |

## Em teste

- **Rodada A — visual 2026** (aguardando CI + teste no carro): trilho lateral como padrão no AA,
  cards com raio 16dp e 6dp entre eles, capa 16:10 arredondada no lugar do ícone (foto preenche;
  ícone fica centrado num fundo tonal), título em negrito, subtítulo cinza, toolbar sem faixa com
  botões redondos tonais. Tudo dentro do tema Moderno; os outros temas não mudam (exceto o trilho
  lateral, que é o padrão do AA para todos).

## Backlog — visual

- Rodada B: texto + pílula de seleção no trilho (`NavButtonView`); player flutuante arredondado
  com play circular (`ControlPanelView`); botão ⋮ visível em cada item.
- Rodada C: overlay de vídeo para toque — play central 96dp, ±10s nas laterais, vídeo em tela
  cheia, título em degradê.
- **IPTV estilo streamer — rodada B1 feita** (TV sempre em cards 16:9, logo inteiro, selo
  "AO VIVO", EPG em lista). Falta: grupos do M3U como abas (B2), programa atual (EPG) e progresso
  no card, hero + fileiras (opção A), mesma grade nas pastas.
- **Filmes estilo streamer:** cards 16:9 com frame do vídeo e progresso; destaque "Continuar
  assistindo" com Continuar / Do início; nome limpo e ano a partir do nome do arquivo.
- **Foto real na pasta:** hoje usa `cover.jpg`/`folder.jpg` se existir (`FolderItem.java:215`),
  senão ícone genérico. Fallback: frame do primeiro vídeo da pasta.
- **YouTube "modo carro":** CSS injetado na página `m.youtube.com` para alvos maiores e menos
  poluição; opção de abrir o vídeo direto em tela cheia no AA (issue upstream #806); usar os
  controles do próprio YouTube em tela cheia (PR upstream #840).
- **Vídeo do YouTube + Waze lado a lado (atração Civic Adventure, carrinho sem motorista):**
  o painel ao lado do mapa no split do AA (Coolwalk) é um card de mídia desenhado pelo próprio AA,
  sem vídeo (inferência, a confirmar). Caminho proposto: **espelhamento do Fermata em paisagem**
  (`MirrorDisplay`, `MirrorServiceFS`) + **tela dividida do Android no celular** (Waze | YouTube),
  e a central mostra as duas metades. Testar primeiro sem código; se funcionar, polir (paisagem
  por padrão, barras escondidas, offset de toque — upstream #796). Como o APK roda em qualquer
  carro: o Andro decidiu **ligado por padrão**, com chave nas Configurações para desligar.
- YouTube com Waze no split do AA — áudio + controles no card de mídia (upstream #759).
- Pôster oficial de filme via TMDB (precisa de chave de API do Andro) — recurso novo, depois.

## Backlog — bugs

- **Legenda parava de aparecer** em velocidade fracionada (1,5x, 0,7x...): o atraso dividido pela
  velocidade arredondava para baixo, o agendador acordava 1ms antes do fim da legenda e cancelava
  o worker. O 1º fix (`98bdf02`) trocou o cancelamento por "rodar já" e virou busy loop na mesma
  legenda. Corrigido de vez: atraso arredondado para cima (mín. 1ms) e a legenda que já está na
  tela não é reentregue. `SubtitlesTest.testScheduler` agora roda em relógio virtual (determinístico,
  sem travar o CI) com 7 combinações de atraso/velocidade; falha nas duas versões antigas.
- **Permissão pedida a cada abertura:** corrigido em `f435a84` (pedia permissões que o sistema
  não concede mais, e repetia a lista inteira).

- **Login do Google no navegador interno** ("Esse navegador ou app pode não ser seguro"): em teste.
  O `X-Requested-With` já foi descontinuado pela WebView (fonte do androidx.webkit) — a hipótese
  inicial estava errada. O sinal que sobra são os client hints (`Sec-CH-UA`,
  `navigator.userAgentData`) com a marca "Android WebView": agora reportados como "Google Chrome"
  (`FermataWebView.hideWebViewBrand`). Contorna a checagem do Google; pode parar de funcionar.
- Botão flutuante semitransparente cobre o conteúdo no celular (no print, em cima de "Termos").

- Crashes de navegação fora do YouTube ("voltando/indo/mudando") — **aguardando `Fermata.log`**
  do Andro (Configurações → Abrir log) com o passo a passo de antes do crash.
- Pular anúncio do YouTube: em teste — `MutationObserver` que vigia o player a página toda, clica
  em "Pular" ou avança o anúncio (`YoutubeWebView.injectAdSkip`). Ainda vale testar o user agent
  do AndrosBrowser (hipótese de por que lá não aparece anúncio).
- Limiar de swipe em px fixo (`GestureListener.java:43`, 5000 px/s): converter para dp usando a
  densidade da tela do carro, não a do celular.
- Atraso de 300ms no toque de canal de stream (`MediaLibFragment.java:587`) para distinguir do
  toque duplo que abre o guia: trocar por botão de guia explícito.
- Upstream #803 (canal IPTV abre como pasta no AA) e #812 (TV ao vivo se perde no AA): verificar
  depois do #839 com a playlist do Andro.
- Remover pasta/playlist sem confirmação; "Excluir" é o primeiro item do menu e o foco vai no OK.

## Backlog — celular (retrato)

Proposta aprovada para desenhar (simulação enviada): título grande na tela, barra inferior com 5
itens + "Mais" com pílula e texto sempre visível (hoje encolhe e esconde o resto atrás de "›"),
mini-player flutuante, tela "Tocando agora" com capa grande, play 84dp e aleatório / repetir /
velocidade / timer à vista, sem o botão flutuante cobrindo a página.

v2 (feedback do Andro "diminua os menus, botões ruins, YouTube de cara"): barra inferior compacta
64dp (ícones 20dp, pílula 48×26, texto 11sp), **YouTube como 1º item e tela inicial** (ligar
`YT_OPEN_ON_START` por padrão — `YoutubeAddon.java:42` — e mudar a ordem padrão da barra),
mini-player 56dp, botões do topo 38dp, aleatório/repetir/velocidade/timer como botões de ícone.

## Backlog — técnico

- **Testes no CI (run de bc3e720):** utils 177 testes — 169 ok, 3 pulados pelo autor, 8 falham:
  todas as variações de `HttpHandlerTest.testHttpConnection2`, que chama
  `http(s)://localhost/tmp_redirect.html` e só passa com o servidor da máquina do autor
  (`ConnectException` no CI). Não desabilitado; para virar teste de verdade, o próprio teste
  precisa subir o servidor que espera. App: `SubtitlesTest.testScheduler` travava — bug real no
  `SubScheduler` (ver bugs), corrigido.

- Validação local do checksum do `gradle-wrapper.jar` (o proxy da sessão bloqueia
  `downloads.gradle.org`; o CI valida).
- Acompanhar os PRs upstream mesclados aqui (#839, #841, #844): quando o Andrey aceitar, o merge
  do upstream fica limpo.

## Backlog — rodada IPTV/ChatGPT (out/2026)

- **Player: "Tocando: origem · título" no painel.** Quando o que toca vem de outra tela (YouTube
  tocando enquanto você está no IPTV), o painel não diz o que é nem de onde veio. Proposta: uma
  linha fina "▶ YouTube · título" no painel; tocar nela leva à tela de origem
  (`MainActivityDelegate.goToCurrent()`). O painel continua global: esconder ou pausar ao trocar
  de tela foram descartados (fica sem controle / quebra ouvir enquanto navega).
- **Filme do IPTV com ~10 s de tela preta antes de começar** (gravação de 17/10). Precisa do
  `Fermata.log` de uma abertura lenta.
- **Vídeo do YouTube parado em 00:00** no fim da mesma gravação. Precisa do log.
- **Login com a conta do ChatGPT** (uso no plano do Andro, sem chave da API): a OpenAI abriu no
  DevDay (29/09/2026) para apps cadastrados, precisa de client id. As requisições já pegam a
  autorização de `ChatAddon.getAuthorization()`; o token OAuth entra ali.
- **Configurações:** um card grande por linha, repetitivo. Agrupar linhas, linhas mais leves,
  ícones por seção.
- **Produto próprio (quando for):** tirar a verificação de atualização do Fermata original e o link
  para o projeto dele; revisar a licença (fork GPL-3.0: manter GPL e créditos, ou reescrever do
  zero).
- **CI:** os 8 `HttpHandlerTest` param o job de testes antes de `:xtream` rodar; rodar `:xtream`
  primeiro ou corrigir esses 8.

- **YouTube "igual ao app":** o que roda é o site m.youtube.com, o mais perto do app que dá sem
  um cliente próprio. Tela a tela idêntica exigiria um cliente nativo sobre a API interna do
  YouTube (Innertube): grande, quebra a cada mudança deles e fere os termos. Ajustes de CSS
  pontuais podem entrar quando houver um alvo concreto.

## Aguardando o Andro

- Instalar o APK arm64 e mandar fotos da biblioteca, do vídeo tocando e do YouTube no Civic.
- `Fermata.log` de um crash, com o que foi feito antes.
- O M3U tem `tvg-logo`? Usa EPG (XMLTV)?
- Está logado no YouTube (Premium?) no AndrosBrowser, no Fermata ou nos dois?
