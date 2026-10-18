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

- [ ] Player (tema Moderno): botões em pílulas arredondadas, play/pause claro e destacado, barra de progresso fina azul com bolinha, tempos menores em cinza, título sobre o vídeo com sombra (sem a faixa preta). Painel continua com 64dp de altura.

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
- [ ] Som de novo: abrir vídeo pelo feed (prévia muda), pelo próximo/anterior, depois de um anúncio: sempre com som, sem tocar em nada. Silenciar pelo YouTube: fica mudo até o próximo vídeo. Log: "Fermata unmute".
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
- [ ] Num vídeo, tocar de novo em **YouTube** no trilho: vai para a home do YouTube (como a aba Início do app). Na home, tocar de novo: volta ao topo. Em tela cheia: o 1º toque sai da tela cheia, o 2º vai para a home.
- [ ] Vídeo que demora a carregar (rede fraca): começa sozinho, sem apertar play (o app insiste por até 30 s).
- [ ] Pausar pelo YouTube (toque) ou pelo painel/volante: o vídeo fica pausado (o autoplay não despausa).
- [ ] Depois de muito tempo sem tocar na tela: o "Continuar assistindo?" do YouTube é respondido sozinho e o vídeo segue.
- [ ] Próximo vídeo (fim do vídeo ou botão próximo): troca mais rápido; mandar Fermata.log ("Fermata autoplay: … playing after N ms").

## 4. IPTV

- [ ] Abrir TV pela 1ª vez com internet: aparece "Canais abertos do Brasil (iptv-org)" ao lado da sua lista; abrir 3 canais.
- [ ] Menu do trilho, na TV: "Adicionar lista IPTV (M3U)" e "Gerenciar listas IPTV" → lista → Editar / Excluir (pede confirmação).
- [ ] Adicionar lista: entrar em EPG ou Conexão e apertar voltar: volta para o formulário (não fecha). Voltar no formulário: volta para a TV.
- [ ] Adicionar uma 2ª lista: o formulário abre vazio (não vem com a anterior).
- [ ] Botão **+** na barra de cima da TV (em qualquer tela da TV) abre "Adicionar lista IPTV"; o item também está no Menu dentro de listas e grupos.
- [ ] Adicionar lista só colando o link (Nome vazio): o ✓ aparece em cima à direita; a lista entra com o nome do servidor. Link colado com espaço antes/depois também funciona.
- [ ] Lista de painel que dá 403 no link M3U (get.php com usuário e senha): o app monta a lista pela API Xtream do painel — canais com as categorias do painel, filmes em "Filmes | categoria"; o guia (EPG) do painel é ligado sozinho. Séries ainda não. Log: "Xtream API list: N channels, M movies".
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
- [ ] Pastas em cards 16:9 com o quadro do vídeo (preenchendo o card); o botão de grade volta para lista se preferir.

- [ ] Lista dos painéis (bestway / nanoignite): **apagar e adicionar de novo**. Aparecem os grupos de canais e "Filmes | …" (antes: lista vazia). Log: "Xtream API list: N channels" e "M3U parsed: …: N entries" com N > 0.
- [ ] Abrir a lista não fecha o app (antes: falta de memória com a lista inteira).
- [ ] TV: os cards mostram nome e ícone, sem a caixinha de seleção (antes: cards vazios com caixinha).

## 4b. IPTV (Netflix)

- [ ] Barra de navegação: aparece **IPTV** ao lado do YouTube.
- [ ] Abrir IPTV: as listas get.php da TV (bestway / nanoignite) viram contas sozinhas, sem digitar. Sem nenhuma: "Adicionar conta" (link get.php, ou servidor + usuário + senha), com a conta conferida no painel.
- [ ] 1ª vez: emblema + "Carregando o catálogo…" (alguns segundos). Depois: abre na hora, mesmo sem internet.
- [ ] Abas Início / Ao vivo / Filmes / Séries: destaque no topo, fileiras que deslizam para o lado (Filmes novos, Mais bem avaliados, uma por categoria).
- [ ] Canal: um toque e toca. Próximo/anterior no volante troca de canal na mesma categoria.
- [ ] Filme: abre os detalhes (capa, sinopse, nota, duração, elenco) → Assistir. Sair no meio → aparece em "Continuar assistindo" com a barrinha vermelha; tocar ali continua de onde parou. "Do início" recomeça.
- [ ] Série: temporadas e episódios; o botão diz "Assistir T1 E1" ou "Continuar T2 E3". Fim do episódio passa para o próximo.
- [ ] Minha lista: botão nos detalhes, ou toque longo num card. Aparece no Início.
- [ ] Buscar (lupa): "compadecida", "cidade invisivel" sem acento acham.
- [ ] Conta (ícone de pessoa): trocar de conta, atualizar o catálogo, remover; mostra o vencimento.
- [ ] No carro (Android Auto, navegação de mídia): IPTV → conta → Ao vivo / Filmes / Séries → categorias.
- [ ] Mandar o Fermata.log: "Xtream" mostra importação, carga e erros (sem senha).

- [ ] Abas Início / Ao vivo / Filmes / Séries inteiras no celular e no carro (nada cortado pela lupa ou pelo perfil).
- [ ] Barra de navegação: só um botão de IPTV (o "TV" saiu). Comando de voz "TV" abre o IPTV.
- [ ] Em ligação (WhatsApp ou normal), tocar num canal: aviso "o vídeo começa quando ela terminar"; ao desligar, o canal começa sozinho.
- [ ] Primeira carga de um painel novo: anotar o tempo da linha "Xtream: catalog … loaded in" do log.

- [ ] Lista M3U sem senha: IPTV → Contas → Adicionar conta → cole só o link (usuário e senha vazios). Aparece com canais, filmes e séries separados pelos grupos da lista; episódios "S01E02" viram uma série com temporadas.
- [ ] Uma lista M3U simples que já estava no TV aparece sozinha nas contas do IPTV.
- [ ] Link errado ou página de login: aviso "O link não trouxe uma lista M3U", e o formulário volta preenchido.

## 5. Vídeo + Waze (atração)

- [ ] Espelhamento do Fermata em paisagem + tela dividida do celular (Waze | YouTube).
- [ ] Toque nas duas metades cai no lugar certo (#796).

## 6. Visual

- [ ] Pastas → adicionar pasta: lista "Armazenamento interno", cartão SD/USB (se tiver), Downloads, Filmes, Músicas, Câmera. Sem "0", "mnt" ou "me.aap.fermata…".

- [ ] Formulário de lista IPTV: só Nome, Localização e "Avançado" (EPG, Catchup, Logos e Conexão dentro dele). Voltar dentro de Avançado volta ao formulário.
- [ ] Menus (Menu do trilho, toque longo num item): itens maiores (~48dp), texto mais claro, fácil de acertar no carro.
- [ ] Textos em português do Brasil: "Configurações" (não "Definições"), "Tela cheia", "Arquivo", "Compartilhar", "Baixando…". Nenhum texto em inglês nos menus, nas configurações do YouTube e da TV.

- [ ] Texto e ícones legíveis de dia e à noite.
- [ ] Botões do painel e do trilho fáceis de acertar.
- [ ] Nada cortado ou sobreposto em 800×480.
- [ ] Ícone novo (emblema H) no celular e no launcher do AA; card de mídia do AA sem capa mostra o emblema.
- [ ] Logo antigo sumiu: ícone da notificação (barra de status e AA) é o H; ícones Espelhar / Início / Voltar no launcher do AA com o fundo escuro novo.
- [ ] Vídeo/áudio sem capa na lista: emblema no card (não o ícone genérico); vídeo com capa continua com a foto.
- [ ] Ao carregar (YouTube, vídeo, canal): emblema "respirando" com um brilho passando no cromo, sem esticar; some quando carrega.
