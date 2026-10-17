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
- **IPTV estilo streamer:** card com logo do canal, "AO VIVO", programa atual (EPG) e progresso;
  grupos do M3U como abas. Depende de o M3U ter `tvg-logo` e EPG — *perguntar ao Andro*.
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

- Crashes de navegação fora do YouTube ("voltando/indo/mudando") — **aguardando `Fermata.log`**
  do Andro (Configurações → Abrir log) com o passo a passo de antes do crash.
- Pular anúncio do YouTube frágil: roda uma vez só no início (`YoutubeMediaEngine.java:77`).
  Trocar por `MutationObserver` + clicar em "Pular". Testar antes o user agent do AndrosBrowser
  (hipótese de por que lá não aparece anúncio).
- Limiar de swipe em px fixo (`GestureListener.java:43`, 5000 px/s): converter para dp usando a
  densidade da tela do carro, não a do celular.
- Atraso de 300ms no toque de canal de stream (`MediaLibFragment.java:587`) para distinguir do
  toque duplo que abre o guia: trocar por botão de guia explícito.
- Upstream #803 (canal IPTV abre como pasta no AA) e #812 (TV ao vivo se perde no AA): verificar
  depois do #839 com a playlist do Andro.
- Remover pasta/playlist sem confirmação; "Excluir" é o primeiro item do menu e o foco vai no OK.

## Backlog — técnico

- Validação local do checksum do `gradle-wrapper.jar` (o proxy da sessão bloqueia
  `downloads.gradle.org`; o CI valida).
- Acompanhar os PRs upstream mesclados aqui (#839, #841, #844): quando o Andrey aceitar, o merge
  do upstream fica limpo.

## Aguardando o Andro

- Criar o secret `FERMATA_SIGNING_SEED` no repositório e avisar (dispara um build assinado).
- Instalar o APK arm64 e mandar fotos da biblioteca, do vídeo tocando e do YouTube no Civic.
- `Fermata.log` de um crash, com o que foi feito antes.
- O M3U tem `tvg-logo`? Usa EPG (XMLTV)?
- Está logado no YouTube (Premium?) no AndrosBrowser, no Fermata ou nos dois?
