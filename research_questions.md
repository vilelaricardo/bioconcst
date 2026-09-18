Questões de Pesquisa:

1. Estratégia elitista vs Estratégia de pop. flutuante
   1. Esta aqui é naquela ideia de guardar sempre os melhores indivíduos ao longo das gerações, principalmente em termos de originalidade.. para não perder os únicos ao longo..
2. Fuzzy com variaveis atualizadas vs. variáveis antigas
   1. a ideia é demonstrar como a mudança das variáveis linguisticas ajudou ou não
3. Estratégia Multi objetivo vs. single objetivo
   1. A ideia anterior era apenas entregar o dado e o caminho executável... mas olhando apenas para evolução com o dado de teste.. a ideia agora é mostrar como a otimização olhando para ambos os casos ajudam na cobertura
4. Estratégia com mutação com LLM e sem mutação com LLM
5. Controle explícito (gene evoluível) vs. aleatoriedade implícita do ambiente
   1. Independente do LLM: transformar algo que já variava "de graça" a cada execução (ex.: ordem de chegada numa race) em um gene evoluível pode reduzir a exploração em vez de ajudar, se a taxa de mutação for baixa - achado do piloto do RQ4 em 2026-09-06.

---

## Piloto preliminar (2026-09-06, noite) - quorum-handshake, GA_COVINST

Todos os números abaixo são cobertura máxima observada por execução (%), benchmark quorum-handshake. Onde diz N=15, é a amostra que sobreviveu ao teste de robustez (rodei de novo com mais execuções pra confirmar); onde diz só N=5, ainda não recebeu esse reforço.

**RQ1 - HallOfFame ligado vs. desligado** (30 gerações):
- N=5: com HoF 70.0% vs. sem HoF 63.3% (parecia um efeito real de +6.7 pontos)
- **N=15 (refeito pra confirmar): com HoF 66.1% vs. sem HoF 66.1% — efeito desapareceu.** Conclusão: no quorum-handshake, com essa configuração, não dá pra afirmar que o HallOfFame ajuda - o resultado de N=5 era ruído de amostra pequena, não sinal.

**RQ2 - Fuzzy com variáveis atualizadas (atual) vs. antigas (pré-recalibração)**:
- 30 gerações, N=5: atual 70.0% vs. antigo 93.3%
- 30 gerações, N=15 (refeito pra confirmar): atual 66.1% vs. antigo **90.0%** - efeito não só se mantém como fica maior
- 100 gerações, N=5: atual 76.7% vs. antigo **98.3%**
- A recalibração do fuzzy (commit de 02/09) foi feita e validada usando o ValiPar - o CoverageInst só foi criado dois dias depois (04/09). Nenhuma das duas versões do `.FCL` tinha sido calibrada pros números reais do CoverageInst.
- **Tentei recalibrar de verdade pro CoverageInst**: instrumentei o código pra capturar a distância/cobertura de TODOS os indivíduos (não só o melhor por geração) em 18 benchmarks - 7.840 amostras reais. A distância real (excluindo timeout) chega a ~0.89, quase o dobro do teto atual (0.45). Tentei recalibrar com esse dado: dobrar só o teto do fitness piorou (61.1%); dobrar o fitness E manter a cobertura exigente do antigo (High precisa de 60-80%, não 35-55%) deu 76.1% - melhor que o atual quebrado, mas ainda abaixo do antigo puro (81.7-90.0%).
- **Decisão final: voltamos pro `.FCL` original (antes do rescale de 02/09) — nenhuma recalibração testada bateu ele.** O arquivo `fuzzy/fuzzySelector.FCL` já está revertido (valores idênticos ao original, só os comentários documentam o porquê). O rescale de 02/09 foi um fix real e bem justificado pro ValiPar na época, mas não se sustentou empiricamente pro CoverageInst.

**RQ4 - GA puro vs. GA com gene de race + mutação (aleatória / via LLM)**, N=5 cada braço, 30 gerações:
- Baseline (sem gene): 70.0%
- Gene de race + mutação aleatória: 60.0%
- Gene de race + mutação via LLM (chain-of-thought, Qwen2.5-Coder 7B no Legion): 60.0%
- **Achado honesto: nem aleatório nem LLM superam o baseline nessa configuração, e o LLM não bate a mutação aleatória.** Ainda em N=5 - dado o padrão do RQ1 (efeito de N=5 que sumiu em N=15), esse resultado merece a mesma checagem antes de virar conclusão definitiva - decisão de quanto investir em mais execuções (cada uma com LLM leva ~10min) fica em aberto.
- Durante a implementação, achei e corrigi 4 bugs reais (um deles - um erro de contagem de genes - quebrava silenciosamente 3 outros benchmarks fora desse experimento: gcdmaster, gcd-two-slaves, gcd-lcm-both; já corrigido e confirmado).

**RQ5** usa os mesmos dados do RQ4 (braço baseline vs. braço aleatório): a diferença de 70.0% vs 60.0% é o mesmo achado - controle explícito de algo que antes era "de graça" pode reduzir exploração em vez de ajudar.

**RQ3** (multi-objetivo) - não implementado ainda, fica pra uma conversa de desenho antes de mexer em código (é a mudança mais invasiva das cinco).

---

## RQ2.1 - FuzzyST vs. Elitismo, replicando o achado do artigo de 2022 (2026-09-06)

No artigo de 2022, FuzzyST e Elitismo (ambos usando o `EliteSelector` do Jenetics, confirmado que é a mesma implementação) não tinham diferença significativa. Testei de novo sob CoverageInst:

- **quorum-handshake** (benchmark sintético, NÃO estava nos 14 benchmarks originais do artigo), N=15, 30 gerações: FuzzyST 81.7-90.0% vs. Elitismo **63.3%** — diferença grande.
- **gcdmaster** (um dos 14 benchmarks ORIGINAIS do artigo), N=15, 30 gerações: FuzzyST **100% (15/15)** vs. Elitismo **100% (15/15)** — **zero diferença**, exatamente como em 2022.

**Conclusão pra justificar no artigo novo**: o achado de 2022 ("sem diferença significativa") se replica perfeitamente nos benchmarks clássicos, mesmo trocando ValiPar por CoverageInst. A diferença que aparece é específica dos benchmarks sintéticos novos (quorum-handshake e provavelmente combined-handshake/threshold-handshake, que compartilham a mesma estrutura de alvo raro/composto — dois eventos precisam coincidir). Narrativa proposta: **o FuzzyST não muda o resultado no conjunto clássico, mas se distingue do Elitismo especificamente nos cenários de alvo raro/composto que os benchmarks sintéticos foram desenhados pra testar** — contribuição adicional, não contradição do resultado de 2022.

Ainda não testado: combined-handshake/threshold-handshake (mesma família do quorum-handshake, reforçaria que é sobre a estrutura do alvo, não um benchmark específico); replicar com os hiperparâmetros exatos do artigo (população=10, gerações=100, N=20) em vez dos usados aqui (população=16, gerações=30, N=15).

---

## Por que não usamos benchmark de terceiro, e o que o critério de cobertura pega (2026-09-06/07)

Investigação extensa (JaConTeBe, IBM Concurrency Benchmark, ZooKeeper, JGroups,
Hazelcast, `AppenderSkeleton` do Log4j, implementações de Raft/Paxos no
GitHub) concluiu que **não existe benchmark de terceiro portável** pra este
critério de cobertura. Duas razões estruturais, não falta de busca:

1. **A maioria dos bugs reais de concorrência depende de sincronização
   FALTANDO** (o caso do double-checked-locking do DBeaver; boa parte dos
   races do JaConTeBe) — fora do escopo por construção: o critério só define
   requisitos sobre pontos de sincronização que EXISTEM no código.
2. **Projetos reais com comunicação por mensagem escondem a chamada de
   socket bruto atrás de uma camada de transporte** (JGroups, ZooKeeper,
   Hazelcast) — o `SyncPointMatcher` só reconhece `DatagramSocket`/
   `DatagramChannel` diretos, nunca uma abstração de protocolo.

**Achado estrutural confirmado por código** (`CoverageEvaluator.java`): o
kind `IDENTITY` (Semaphore/Lock/Condition/CyclicBarrier/`synchronized`) é
**cego a ordem** — correlaciona só por identidade de objeto (`SEM_RELEASE`/
`SEM_ACQUIRE` tocaram o mesmo objeto?), sem timestamp, sem sequência, sem
"quem chegou primeiro". Só o kind `MESSAGE` (send/receive direcional,
sender/receiver específico) consegue representar uma corrida genuína. Isso
restringe o domínio real de aplicação da ferramenta a **passagem de
mensagens com múltiplos remetentes legítimos pra um mesmo ponto de
recebimento** — exatamente a forma do `quorum-handshake`.

**Trabalhos que complementam essa lacuna** (pra seção de limitações/
trabalhos futuros - resumo achado por busca, PDF completo não acessível no
momento, Wiley paywalled e o mirror do autor bloqueou fetch automatizado):

- **Taylor, Levine & Kelly, "Structural Testing of Concurrent Programs"**
  (IEEE TSE, 1992) — o trabalho fundador de toda essa linhagem (de onde vem
  o PCFG da Simone Souza). Já definia uma hierarquia com "synchronization
  coverage" (o que a Simone usa, e o que implementamos) e também
  "all-du-path coverage" — cobertura de pares definição-uso em variáveis
  compartilhadas, sem exigir sincronização no ponto de uso.
- **Yang & Pollock, "All-uses testing of shared memory parallel programs"**
  (Software Testing, Verification and Reliability, 2003, vol 13(1):3-24) —
  estende esse conceito especificamente pra memória compartilhada em
  paralelo: testa pares definição-uso independente de existir sincronização
  protegendo a variável ou não. É o candidato mais direto pra pegar o caso
  DBearay-like (escrita protegida, leitura desprotegida) que o nosso
  critério não vê.
- **Lei & Carver, "Reachability Testing of Concurrent Programs"** (IEEE TSE,
  2006, vol 32(6):382-403; sobre a técnica original de Carver & Tai) —
  confirmado (via busca, resumo do abstract) que o modelo deles **trata
  explicitamente semáforos, locks E monitores** (não só passagem de
  mensagem), e que "durante o reachability testing, toda sequência de
  sincronização PARCIALMENTE ORDENADA de um programa com uma entrada dada é
  exercitada exatamente uma vez" — ou seja, ao contrário do nosso
  `IDENTITY`, o modelo deles enxerga ORDEM entre eventos de sincronização
  tipo lock/semáforo, não só co-ocorrência. Esse é o candidato certo pra
  citar se algum dia quisermos estender o `IDENTITY` pra capturar "quem
  ganhou a disputa pelo lock" (não implementado, fica como trabalho futuro).

**Resumo pra tese**: sync-edge coverage (o nosso) pega bem uma classe
específica e real — corrida de ordem de entrega em comunicação por
mensagem — e serve de forma secundária pra alcançabilidade de recursos de
exclusão mútua, mas não é um critério geral de detecção de concorrência.
Taylor-Levine-Kelly/Yang cobrem o eixo de dados compartilhados sem exigir
sincronização; Lei-Carver/Carver-Tai cobrem ordenação pra lock/monitor que
o nosso modelo trata de forma simétrica. É um recorte deliberado, não uma
limitação escondida.

---

## Nova família de benchmarks ancorados em algoritmos reais e citáveis (2026-09-07)

Dado que benchmark de terceiro é inviável, a saída foi **reconstruir com
fidelidade real algoritmos de literatura consagrados e citáveis** — a
credibilidade vem do algoritmo ser peer-reviewed e citado, não do código
ser de terceiro. Todos os três usam `DatagramSocket` puro (kind `MESSAGE`),
mais processos que o `quorum-handshake` original (2 peers) de propósito —
mais processos = mais combinações de ordem de chegada = alvo genuinamente
mais difícil pro GA, não um custo a evitar.

### 1. Raft RequestVote / Eleição de líder
Cita Ongaro & Ousterhout, "In Search of an Understandable Consensus
Algorithm (Extended Version)", 2014, §5.1-5.2. 5 processos (1 Candidate + 4
Peers), maioria de votos (não unanimidade) com threshold de termo ~50%
largo de propósito - dificuldade dominada pela combinatória de ordem de
chegada (até 4! ordenações), não por um alvo raro. **56 required edges,
teto ~100%** (todos alcançáveis).

### 2. Two-Phase Commit
Cita Gray, "Notes on Data Base Operating Systems", 1978. 5 processos (1
Coordinator + 4 Participants), unanimidade (contraste estrutural
deliberado com o Raft) com janela calibrada pra `P(4 YES) ≈ 0.16%` -
mesma probabilidade composta do `quorum-handshake` original, só espalhada
em 4 dimensões em vez de 2 (isola dimensionalidade como variável, não
rarefação). **56 required edges, teto ~100%.**

### 3. Bully Algorithm
Cita Garcia-Molina, "Elections in a Distributed System", IEEE Trans.
Computers, 1982. N=3 processos, uma única classe `Node` genérica (sem
branching por id). **Teto real de só 22,2% (12/54)** - traçado à mão contra
o protocolo real: o `fixedMessageSources` só filtra por PROCESSO, não por
EDGE específica, então tipos de mensagem diferentes (`OK`-reply, `ELECTION`,
`COORDINATOR`) do mesmo remetente casam com o mesmo alvo mesmo nunca se
alcançando de verdade - teto de modelagem (mesma família do teto de 66,67%
do `quorum-handshake`), não fraqueza de busca. Documentado no javadoc do
`Node.java`.

**Bug real encontrado e corrigido durante a construção do Bully**: um nó
"crashado" mantinha o socket principal aberto (o framework só permite
variar argumentos, nunca se o processo é lançado ou não), e uma mensagem
`ELECTION` perdida no buffer era lida por engano pelo `receive()` final,
mascarando-se como um resultado errado sem crashar nada - só apareceu
lendo o log de um teste manualmente, não foi achado pela cobertura em si.
Corrigido com um segundo socket dedicado só ao anúncio final (mesmo padrão
multi-socket que o `roller-coaster` já usa).

**Bug de infraestrutura encontrado rodando em escala no Legion**: com as
três execuções rodando juntas (~70 JVMs pra 12 núcleos), o
`CoverageTracer` tem um timeout fixo de 1500ms pra resolver a identidade de
um processo contra um registro em arquivo - sob essa contención, a escrita
do registro não conseguia ser escalonada a tempo, zerando silenciosamente a
cobertura medida mesmo com os processos funcionando certo. Corrigido:
`REGISTRY_LOOKUP_TIMEOUT_MS` agora é configurável via
`-Dcoverage.registryTimeoutMs` (default continua 1500ms). Lição prática:
rodar um benchmark por vez em escala cheia, não os três juntos.

**Status**: execuções em escala cheia (população=16, 5 execuções cada) em
andamento no Legion, uma de cada vez (Raft → 2PC → Bully).

**Bugs reais de infraestrutura encontrados rodando no Legion pela primeira vez (2026-09-07, madrugada)** - nenhum dos dois é bug de lógica do benchmark, os dois são específicos do ambiente Linux do Legion (nunca apareceram no Mac):
1. **`InetAddress.getLocalHost()` retorna endereço errado no Ubuntu**: o `/etc/hosts` do Legion mapeia o hostname pra `127.0.1.1` (padrão Debian/Ubuntu), não `127.0.0.1` - então tanto o endereço registrado pelos meus 3 benchmarks novos quanto o **próprio `CoverageTracer.registerSocket`/`registerChannel`** (código de infraestrutura, não meu de hoje) caíam nesse mesmo fallback quebrado, travando toda resolução de identidade de processo. Corrigido nos dois lugares trocando por `InetAddress.getLoopbackAddress()` - correto porque todo processo que essa ferramenta lança roda na mesma máquina e fala por loopback, nunca é distribuído de verdade entre hosts. Sem regressão (115/115 testes, suite completa).
2. Já documentado acima: `REGISTRY_LOOKUP_TIMEOUT_MS` hardcoded em 1500ms, agora configurável via `-Dcoverage.registryTimeoutMs`.

**Raft - resultado final (2026-09-07, N=5, população=16, 50 gerações, Legion)**:
| Execução | Cobertura máxima | Geração em que bateu o máximo |
|---|---|---|
| 0 | 98.2% | 10 |
| 1 | 94.6% | 1 |
| 2 | 94.6% | 15 |
| 3 | 96.4% | 2 |
| 4 | 94.6% | 6 |

Média **95.7%**. Achado real e interessante, diferente dos dois padrões já vistos: **não é "resolve de cara e trava lá" (tipo gcdmaster/Jacobi) nem "demora muitas gerações pra subir aos poucos"** - a geração 1 já bate valores altos (87-96%) em toda execução, mas a cobertura **oscila continuamente entre ~82-98% pelas 50 gerações inteiras, sem nunca cristalizar em 100%** nem uma única vez em nenhuma das 5 execuções. Explicação provável: com `raceGene` desligado (decisão deliberada, ver achado 2 acima), a ordem de chegada dos 4 peers depende só do jitter natural do SO a cada execução de teste - com 56 arestas exigidas (a maioria ligada à combinatória de até 4! ordenações), a população inteira de 16 indivíduos demonstrar coletivamente TODAS numa mesma geração é raro por acaso, então a união de cobertura "pisca" em vez de convergir e ficar parada como o HallOfFame normalmente sustenta pra um alvo único.

**Two-Phase Commit - resultado final (2026-09-07, N=5, população=16, 50 gerações, Legion)**:
| Execução | Cobertura máxima | Geração em que bateu o máximo |
|---|---|---|
| 0 | 92.9% | 42 |
| 1 | 94.6% | 24 |
| 2 | 96.4% | 31 |
| 3 | 89.3% | 24 |
| 4 | 89.3% | 17 |

Média **92.5%**, ligeiramente abaixo do Raft (95.7%) - dentro do esperado, não uma diferença grande. **A diferença real e interessante está em QUANDO bate o máximo, não no valor final**: Raft bateu seu máximo nas gerações 1, 2, 6, 10, 15 (cedo, consistente com dificuldade dominada por combinatória de ordem - já disponível desde o início); 2PC bateu o dele nas gerações 17, 24, 24, 31, 42 (bem mais tarde, consistente com dificuldade dominada por raridade do ramo COMMIT - precisa esperar o evento raro acontecer por acaso). A execução 0 do 2PC mostra isso vividamente: banda baixa (68-82%) na maior parte do tempo, com dois picos isolados (geração 15: 91,1%; geração 42-43: 92,9%) exatamente quando o ramo raro foi tocado. **Essa diferença de velocidade de convergência é o achado mais limpo dos dois benchmarks** - confirma que a dificuldade de cada um vem de fontes estruturalmente diferentes, como o desenho pretendia.

**Bully - resultado final (2026-09-07, N=5, população=16, 40 gerações, Legion)**:
| Execução | Cobertura máxima | Geração em que bateu |
|---|---|---|
| 0-4 (todas idênticas) | 35.2% (19/54) | 1-4 |

Teto real empírico é **35,2% (19/54), não os 22,2% (12/54) que calculei à mão** - meu rastreamento manual do protocolo subcontou combinações reais alcançáveis (não refiz o cálculo a mão de novo, o número empírico, consistente e idêntico nas 5 execuções independentes, é mais confiável que a conta manual). De qualquer forma, o padrão se repete: **bate o teto entre a geração 1 e 4, e nunca mais sai dali em nenhuma das 5 execuções, em 40 gerações inteiras** - exatamente o mesmo "resolve de cara, satura" que o Raft mostrou.

## Achado crítico: "oscila sem convergir" era, na real, "resolvido por sorte" - teste de linha de base aleatória (2026-09-07)

Pergunta direta do usuário: os programas não estão sendo TOTALMENTE cobertos já entre a geração 1-3? Isso reabriu a questão de fundo: **o benchmark discrimina busca inteligente de busca aleatória, ou não?** Testado com uma linha de base de busca puramente aleatória - mesmo orçamento total (população=800, 1 geração só, sem seleção/cruzamento/mutação nenhuma) rodado no Legion:

- **Raft**: 800 indivíduos aleatórios → **100% de cobertura numa tacada só.** O GA de 50 gerações (mesmo orçamento total: 16×50=800) NUNCA bateu 100% em nenhuma geração de nenhuma das 5 execuções (máx. 98,2%).

**Explicação encontrada (pergunta do usuário: "qual o domínio de entrada desse programa?")**: o domínio NOMINAL é gigante (`1000⁴=10¹²` pro Raft), mas o domínio EFETIVO que o critério de cobertura enxerga é minúsculo - cada peer só toma uma decisão BINÁRIA (`grant` se termo<500, senão `deny`, um corte 50/50 bem no meio do range, desenhado assim de propósito pra "não ser sobre raridade, só sobre combinatória de ordem"). Isso colapsa o espaço relevante pra ~2⁴×4!≈384 células, um problema de "colecionador de cupons" trivialmente saturável por 800 amostras aleatórias - não é a busca sendo inteligente, é o espaço relevante ser pequeno demais pra precisar de busca nenhuma.

**Comparação com o 2PC**: diferente do Raft, o 2PC usa uma janela ESTREITA `[400,599]` de `[0,999]` (não um corte 50/50) - o alvo raro de verdade (0,16% por amostra) deveria, em tese, resistir mais a esse mesmo teste. **Também bateu 100% na primeira tentativa** - mas aqui o resultado é MENOS conclusivo que o do Raft: com p=0,16% por amostra, a chance de acertar pelo menos uma vez em 800 amostras é só ~72% (`1-(1-0.0016)^800`), não quase-certeza como no Raft (que tem uma taxa de acerto por amostra muito maior, dado o corte 50/50). Ou seja, pode ter sido sorte dessa vez específica - precisa repetir a linha de base do 2PC várias vezes pra medir a taxa real de sucesso, não confiar numa amostra só (mesma lição de N pequeno de sempre). Baseline do Bully rodando agora.

**Implicação, ainda em aberto**: se até o Raft (desenhado pra ser "mais difícil" que o quorum-handshake original) cai nessa armadilha, o corte 50/50 no limiar de decisão foi um erro de calibração - a dificuldade que eu queria isolar (combinatória de ordem de chegada) acabou trivialmente saturável por amostragem, porque o número de "classes de equivalência" relevantes ficou pequeno demais. Precisa decidir: estreitar os limiares de decisão do Raft (deixar de ser 50/50), ou aceitar que a combinatória de ordem sozinha nunca vai ser um alvo difícil o suficiente sem também estreitar a rarefação do voto.

**Bully confirma o mesmo padrão**: linha de base aleatória (800 indivíduos, 1 geração) bateu **exatamente 35,2%** - o MESMO valor que as 5 execuções completas do GA (16×40 gerações) alcançaram e nunca superaram. **Os três benchmarks novos (Raft, 2PC, Bully), sem exceção, têm seu teto de cobertura alcançado por amostragem aleatória pura, sem nenhuma vantagem mensurável de rodar o GA de verdade.** Isso é um achado sério: nenhum dos três, do jeito que foram calibrados, serve pra demonstrar que a busca evolutiva (seleção, cruzamento, mutação, FuzzySelector) faz alguma diferença sobre amostragem aleatória com o mesmo orçamento total. Precisa de recalibração (janelas mais estreitas, ou espaço de decisão maior) antes de usar esses três como evidência de que o GA é melhor que aleatório - do jeito que estão, eles só provam que o critério de cobertura é alcançável, não que a busca importa.

## Bug real de infraestrutura encontrado num debug detalhado (2026-09-07) - endereço de rede inconsistente

Durante uma sessão de debug pedida pelo usuário (rastro geração-a-geração do GA, ver `debug-geracao-quorum-handshake.md`), o `quorum-handshake` no Mac de repente mostrou **0% de cobertura em TODOS os indivíduos** de 3 gerações - um resultado muito diferente dos 66-98% já documentados a noite toda. Investigado e corrigido:

- **Causa raiz**: `Coordinator.java`/`Peer.java` do `quorum-handshake` (e, descobriu-se, de mais 24 outros arquivos de benchmark no repositório) usam `InetAddress.getLocalHost()` pra trocar endereço entre os processos - o MESMO padrão frágil já corrigido horas antes no `CoverageTracer` (infraestrutura), por causa de um bug parecido achado rodando no Legion (`/etc/hosts` do Ubuntu mapeando o hostname pra `127.0.1.1`). No Mac, momentos antes desse debug, `getLocalHost()` passou a resolver pro IP real da rede (`192.168.68.54`, mesma sub-rede do Legion - possivelmente por causa de uma reconexão de Wi-Fi durante a limpeza de swap/Safari feita pouco antes) - **inconsistente** com o registro interno do `CoverageTracer` (já usando loopback desde a correção do Legion), quebrando toda correlação de identidade de processo.
- **Corrigido em 26 arquivos** de benchmark no repositório inteiro (não só o quorum-handshake) - `quorum-handshake`, `combined-handshake`, toda a família `token-ring-*`, `gcd-lcm*`, `roller-coaster`, `sieve` - trocando `InetAddress.getLocalHost()` por `InetAddress.getLoopbackAddress()`, já que todo processo desses benchmarks roda na mesma máquina.
- **Os resultados já registrados nesta sessão (Raft/2PC/Bully completos, RQ1/RQ2/RQ2.1 etc.) continuam válidos** - esse bug só se manifestou nessa janela específica de tempo no Mac, não durante nenhuma das execuções já documentadas.
- Confirmado corrigido: após a correção, o mesmo traço de debug voltou a mostrar cobertura real (33,3%, os elementos do ramo NORMAL alcançáveis sem precisar da janela rara).

## Achado metodológico: a distância de fitness é cega ao pareamento de mensagens, mesmo no kind MESSAGE (2026-09-07)

Aprofundando o mesmo debug detalhado (agora com pop=12, 7 gerações, e um detalhamento por aresta adicionado ao `DebugGenerationTrace.java` que expõe o `GraphDistance.compute()` de cada aresta `MESSAGE` individualmente, não só a soma agregada), surgiu a pergunta: a distância de fitness já considera a parte sequencial/intraprocesso do código, ou só pensa em arestas de sincronização?

**Resposta, com números reais**: as duas coisas são verdadeiras ao mesmo tempo, e a diferença importa.

- Pra decisões *numéricas dentro de um processo* (ex.: `Peer` decidindo se seu valor cai na janela `[480,519]`), a distância dá gradiente real e útil - via `BranchDistance` sobre os operandos observados no ponto de divergência. Nas 7 gerações rodadas, os elementos que dependiam de um Peer entrar na janela mostraram distância `0,063`, nunca zero, mesmo nunca cobertos.
- Mas para o **pareamento entre lados de uma aresta `MESSAGE`** - ou seja, a corrida propriamente dita, "esse envio específico casou com esse recebimento específico" - a distância é estruturalmente cega. `GraphDistance.sideDistance()` (`src/CoverageInst/GraphDistance.java:60-74`) decide "quanto falta" checando só se cada bloco do caminho mais curto já foi observado **pelo processo**, sem nenhuma correlação de qual mensagem gerou qual observação. Resultado: se o Peer1 já executou seu envio (por outro slot) e o Coordinator já executou seu recebimento naquele slot (por causa do Peer2), a aresta MESSAGE que exigiria especificamente Peer1→aquele slot aparece com `missingCount == 0` → distância `0,0` - o mesmo valor de uma aresta genuinamente coberta - mesmo estando descoberta.

Efeito prático observado: com nenhum dos dois `Peer`s caindo na janela em nenhuma das 7 gerações (amostra pequena, pop=12), a população fica presa alternando entre dois padrões espelhados de "quem chegou primeiro" - decidido por escalonamento do SO/JVM, não pelos genes - e a soma de distância dos dois padrões é **idêntica**, porque as arestas que os diferenciam contam 0,0 nos dois casos. Não existe sinal de fitness que diferencie "quase pareou certo" de "pareou errado" nem que aponte qual padrão está mais perto do outro.

**Isso é distinto do achado de `IDENTITY` vs `MESSAGE`** já registrado acima (seção "Por que não usamos benchmark de terceiro..."): lá, o kind `IDENTITY` é cego a ordem *por desenho* (só correlaciona por `identityHashCode`, nunca teve pretensão de medir corrida). Aqui, o problema aparece **dentro do próprio kind `MESSAGE`**, que é o único desenhado pra representar corrida de verdade - e mesmo assim, uma vez que os dois endpoints (send e receive) já foram fisicamente alcançados por qualquer execução daquele processo, a métrica não tem como expressar "faltou só a ordem/pareamento bater". A causa raiz é que `observedNodesByProcess` é indexado só por `processId`, sem granularidade por evento/mensagem específica.

Detalhamento completo, com a tabela de elementos decodificada e os dois padrões espelhados lado a lado, está no final de `debug-geracao-quorum-handshake.md` ("Conclusão: por que a distância não empurra a busca pra fora do platô de 50%").

**Não investigado ainda**: se dar granularidade por mensagem a `observedNodesByProcess` (ex.: rastrear "este bloco foi observado como parte de QUAL correlação send↔receive candidata") é viável sem reescrever o modelo de coleta de traço, e se resolveria isso sem quebrar o resto do cálculo. Fica como possível item de trabalho futuro, não como correção emergencial - o achado atual é de caracterização, não um bug a corrigir às pressas.

## Correção + implementação: `chainedDistance` resolve o problema acima, e revela um segundo "flag problem" local (2026-09-07)

Implementado de verdade o mecanismo de distância encadeada descrito na seção anterior. Durante a implementação, descobrimos que a explicação original estava **parcialmente errada de um jeito importante**: os elementos 0/1/4/5 (achamos que mostravam "gradiente numérico real" vindo da janela `[480,519]`) na verdade também sofriam do mesmo "flag problem" - só que DENTRO do próprio `Peer.java`, não entre processos. O ponto de divergência real não é a comparação numérica (`value>=480`/`value<=519`, sempre executada incondicionalmente antes do `if`) - é o consumo do booleano `inWindow` já calculado, via `if (inWindow)` (bytecode `IFEQ`, sem gradiente, sempre dando exatamente `0,5`). Essa é a explicação correta pra algo que já estava nos dados o tempo todo: o `0,063` aparecia **idêntico** para valores tão distantes quanto `12` e `479` - um sinal claro, mal-interpretado até agora.

`GraphDistance.ChainedSource` (`src/CoverageInst/GraphDistance.java`) agora suporta duas formas:
- **Cross-processo** (`viaReceiverEdge`/`targetSenderEdge`) - resolve quem realmente enviou pra uma aresta de recebimento nesta execução (dado já calculado por `CoverageEvaluator`, só nunca exposto antes) e empresta recursivamente a `sideDistance` daquele processo.
- **Local** (`localBlock`/`wantedTaken`) - pra quando o flag é calculado e consumido no MESMO processo (o caso do próprio `inWindow` do Peer): aponta direto pro(s) bloco(s) que calculam o booleano de verdade, descobertos via dump de `branchPredicates`/`syncEdgeBlocks`, sem checagem de "estava faltando" (o bloco roda incondicionalmente, ou é pulado por curto-circuito).

Ambos declarados em `config/quorum-handshake-covinst.json`, opt-in por aresta - nenhum dos ~20 benchmarks já migrados é afetado. `chainedDistance` tem PRIORIDADE sobre o predicado local reconhecido automaticamente (não é só um fallback) - `IFEQ`/`IFNE` são opcodes numéricos válidos por si só, então sem essa prioridade o predicado "resolvia com sucesso" usando o flag (sempre 0/1, sem gradiente), mascarando silenciosamente o próprio problema que o mecanismo existe pra resolver.

**Resultado real, mesmo pop=12/7-gerações usado a sessão toda**: a distância dos elementos 0/1/4/5 deixou de ser um valor fixo (`0,0625` sempre) e passou a variar de verdade com o valor bruto do gene - `468` (12 de distância da janela) dá `0,116`; `309` (171 de distância) dá `0,124`. A distância agregada por indivíduo, antes travada em `0,0625` em TODA a sessão, agora varia (`0,0807`-`0,0832` nesta amostra) - o primeiro sinal de fitness genuinamente informativo sobre a distância até a janela desde que o `GA_COVINST` existe. Suíte completa: 119/119 (4 testes novos em `GraphDistanceTest`/`CoverageEvaluatorTest`), zero regressão nos benchmarks que não declaram `chainedDistance`.

**Ainda não testado**: se essa mudança na paisagem de fitness melhora de fato a convergência do GA em escala real (população/gerações normais) - só validamos o mecanismo no nível de "os números fazem sentido", não com um experimento A/B de cobertura. Próximo passo natural: repetir o RQ4/RQ5-style pilot (N=15) comparando quorum-handshake COM e SEM `chainedDistance`, no Legion.

## Sequência de experimentos planejada (2026-09-07) - critério: a mudança em si não justifica um experimento, a FORMA como mudamos sim

Registrando a ordem combinada com o usuário pra quando formos executar - "vamos executar conforme as coisas forem avançando", não necessariamente todos, mas útil ter documentado. Critério de inclusão explícito: só entra o que carrega uma hipótese de pesquisa real sobre a busca/GA - infraestrutura pura (CoverageInst substituindo ValiPar, suporte a `synchronized`, o fix do `getLocalHost`, timeout configurável, a própria existência da família Raft/2PC/Bully) fica de fora; essas coisas HABILITAM experimentos, não são um.

1. **RQ1 - completar a réplica do HallOfFame no benchmark que realmente o motivou.** Já respondido em N=15 no quorum-handshake (sem efeito - ver acima) - mas o commit original que introduziu `HallOfFame` foi justificado por uma observação no `combined-handshake`, nunca testado formalmente. Falta essa réplica antes de fechar a RQ de vez.
2. **RQ6 (novo) - `chainedDistance` vs. penalidade flat.** A mudança de hoje mesmo: hipótese testável ("um gradiente informado pra decisões gated por flag ajuda a busca mais que um valor fixo"), infraestrutura já pronta e validada mecanicamente. A/B no quorum-handshake primeiro (N=15, com/sem `chainedDistance`, tudo mais igual); se o efeito for real, estender pro 2PC/Raft/Bully (mesma estrutura `.equals()`-gated no celebrate/LEADER/COMMIT).
3. **RQ7 (novo, formalizando um achado já feito informalmente) - GA vs. amostragem aleatória, e se o RQ6 muda essa resposta.** Já provamos (seção "Achado crítico" acima) que random empata/vence o GA no Raft/2PC/Bully - mas nunca rodamos essa linha de base no próprio quorum-handshake. Desenho de 3 braços que conecta direto com o RQ6: **(a) random puro, (b) GA sem `chainedDistance`, (c) GA com `chainedDistance`** - testa se o conserto de hoje é especificamente o que faz o GA superar o aleatório nessa janela estreita, não só "os números parecem mais informativos".
4. **RQ2.1 - estender a réplica de FuzzyST vs. Elitismo.** Já fechado pra gcdmaster (sem diferença, replica o artigo de 2022) e quorum-handshake (com diferença, atribuída à estrutura do alvo, não ao tooling). Falta `combined-handshake`/`threshold-handshake` (mesma família) pra reforçar que a diferença é sobre estrutura do alvo (rara/composta) e não um benchmark isolado.
5. **RQ4/RQ5 - LLM como operador de mutação + gene de corrida explícito.** Código de produção já existe, piloto em N=5 não mostrou vantagem sobre mutação aleatória - mas nunca passou pelo mesmo teste de robustez em N=15 que derrubou o resultado do RQ1 na mesma noite. Depende de Legion/Ollama, por isso mais pra frente na fila (custo de execução maior, não de prioridade científica).
6. **RQ3 - multi-objetivo.** Deixado por último de propósito - ainda sem nenhum desenho, é a mudança arquiteturalmente mais invasiva das cinco originais, e as respostas dos itens acima (principalmente RQ6/RQ7) podem informar como desenhar o genótipo de dois cromossomos (ver a nota já registrada sobre a Figura 2 do artigo de 2022 ter previsto isso desde o início).

**RQ2 (calibração do FuzzySelector) não entra nesta fila** - já está respondida e fechada (revertido pro FCL pré-09-02, confirmado 2x em N=15), mantida no documento só como histórico.

## Literatura recém-achada, direto no nosso nicho: Mirhosseini & Haghighi (2020) e Gong et al. (2020) (2026-09-12)

Contexto: buscando validar se o gap "geração de teste via busca para sistemas message-passing" (apontado pela survey de Bianchi/Margara/Pezzè, TSE 2018, seções 6.1 e 6.6, como <5% da literatura) continuava aberto em trabalhos mais recentes, achamos dois papers de 2020 - já salvos em `papers/novo_conc_articles/` - que competem diretamente com esse nicho e, em um dos casos, com o próprio `raceGene` (RQ4/RQ5).

**Mirhosseini & Haghighi, IJCIS 2020** - "A Search-Based Test Data Generation Method for Concurrent Programs". Usa um grafo chamado PCFG (mesma sigla/ideia que a nossa), critério **all-def-s-use** (cobertura de pares def-use que atravessam uma aresta de comunicação - diferente do nosso critério, que cobre a própria topologia de arestas de sincronização via `RequiredElementsGenerator`) e uma fitness de duas partes: `PathSimilarity_Score` (estado discreto 0-5 conforme quanto do caminho-alvo de 3 sub-trechos - antes/aresta/depois da comunicação - foi coberto) + `NBD` (branch distance normalizada, mesma família Korel/Tracey que já usamos). Compara GA/ACO/PSO/SFLA e propõe um híbrido SFLA-VND, em 5 benchmarks (Gcd1/Gcd2/Index/Matrix/SkaMPI1). É mais um paper de "qual metaheurística generaliza melhor pra essa formulação de fitness" do que um ataque específico ao problema de não-determinismo/corrida - eles até têm `receive` com wildcard (nós 5/6 do master de `Gcd1`), mas não aparenta tratar isso como problema central.

**Gong, Pan, Tian et al., Information and Software Technology 2020** - "A feedback-directed method of evolutionary test data generation for parallel programs". Este é o mais próximo do nosso `raceGene`: o indivíduo já codifica **dado de entrada E sequência de escalonamento no mesmo cromossomo** (`X = (x_1,...,x_ns, r_1,...,r_nr)`, onde cada `r_j` é a ordem de chegada de um "Wildcard Receiving Node Group" - exatamente o problema do `receive(ANY)`/`RacePoint` que RQ4/RQ5 ataca). A contribuição deles não é mudar a fitness (usam uma métrica única e mais simples que a nossa - similaridade de maior sub-sequência comum de nós entre caminho-alvo e caminho percorrido, sem branch distance) - é mudar **onde o crossover/mutation atuam**: um arquivo de indivíduos já avaliados informa quais sequências de escalonamento são "boas" (`ESS-GA`), e quais WRNGs especificamente afetam os nós ainda não cobertos do caminho-alvo (`RUS-GA`), pra concentrar os operadores genéticos ali em vez de tratar dado-de-entrada e escalonamento com a mesma probabilidade uniforme. Testado em 11 benchmarks (mais que os nossos atuais), com reduções de até ~50-70% no número de gerações/tempo contra uma GA básica (`BGA`) que trata as duas partes do cromossomo igualmente.

**Por que isso importa pro nosso `raceGene` (RQ4/RQ5)**: o piloto que já rodamos comparou mutação de gene de corrida **aleatória** vs. **guiada por LLM**, e não achou vantagem clara de nenhuma sobre deixar o SO decidir livremente. O Gong et al. 2020 mostra que existe uma **terceira alternativa, já publicada e validada empiricamente**, que nem é aleatória nem é LLM: realocar a probabilidade de operador genético com base em feedback populacional puro (sem custo de LLM nem heurística externa) sobre quais sequências de escalonamento tendem a cobrir mais. Isso é uma hipótese concreta e barata de testar antes de declarar RQ4/RQ5 encerradas - talvez o problema não seja "escalonamento não se beneficia de busca direcionada", e sim "nossas duas tentativas (aleatório, LLM) não eram as formas certas de direcionar".

**O que continua parecendo genuinamente nosso**: nenhum dos dois papers lida com o modo de falha específico que motivou o `chainedDistance` - a distância colapsar pra `0,0` (idêntica a "coberto de verdade") quando os dois lados de uma dependência (aresta MESSAGE, ou flag local tipo `inWindow`) já foram fisicamente alcançados por QUALQUER execução do processo, sem correlação de qual execução gerou qual observação (`GraphDistance.java:60-74`, achado documentado acima em "Achado metodológico..."). A fitness do Mirhosseini/Haghighi (baseada em estado discreto 0-5 por sub-trecho) e a do Gong et al. (similaridade de sub-sequência) têm a mesma classe de problema em potencial - nenhuma delas parece ter um mecanismo equivalente ao `ChainedSource` pra resolver isso -, mas não confirmamos isso lendo o código deles (só o texto do paper), então isso fica como suposição a verificar, não conclusão.

**Não testado ainda**: reimplementar ESS-GA/RUS-GA (realocação de probabilidade de operador por feedback populacional) como um QUARTO braço do experimento de `raceGene`, ao lado de aleatório/LLM/sem-gene-de-corrida, antes de fechar RQ4/RQ5 de vez.

## Desenho formal RQ6/RQ7 (2026-09-13)

Desenho completo (níveis landscape/search/system, 4 braços incluindo um
"GA local-only" sugerido pelo Codex, métricas de orçamento nominal vs.
avaliações reais, predição falsificável sobre o que causalDistance deve e
não deve resolver) está em `rq6-rq7-causaldistance-experiment-design.md`.
Ainda não executado. Motivado por uma revisão do Codex que achou um
overclaim real no artigo: causalDistance resolve o flag problem local e a
propagação cross-process quando a mensagem ainda não chegou, mas NÃO
resolve o pareamento send/receive errado quando ambos os lados já foram
alcançados (elementos 2/3/6/7 do quorum-handshake) — isso é
não-determinismo de escalonamento, não um problema de dado/flag. Texto do
artigo corrigido (`main.tex`, commit `a569b1c`) para não afirmar mais que
um único mecanismo resolve os dois casos.
