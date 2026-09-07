# Debug: rastro geração a geração — quorum-handshake


_Gerado em 2026-09-07T13:10:32.086405_

População reduzida pra 12 indivíduos (em vez do valor real da config) só pra ficar legível — tudo mais (fitness, seleção, cruzamento, mutação) é o motor real de produção, sem simplificação.

**Como ler**: os blocos SOBREVIVENTES/PAIS/CRUZAMENTO/MUTAÇÃO que aparecem ANTES de uma tabela "Geração N" são os eventos que PRODUZIRAM aquela geração a partir da população da geração anterior (ou da população inicial aleatória, no caso da Geração 1, que não é mostrada por si só). A tabela em si é o RESULTADO já avaliado pelo fitness.

**Honestidade**: com só 12 indivíduos e 7 gerações, não é esperado que esse alvo específico (quorum-handshake) avance de verdade - o objetivo aqui é mostrar o MECANISMO funcionando, não uma busca bem-sucedida.

## Elementos exigidos (12 no total)

0. `Peer#main:0@p1 -> Coordinator#main:0@p0`
1. `Peer#main:0@p1 -> Coordinator#main:1@p0`
2. `Peer#main:1@p1 -> Coordinator#main:0@p0`
3. `Peer#main:1@p1 -> Coordinator#main:1@p0`
4. `Peer#main:0@p2 -> Coordinator#main:0@p0`
5. `Peer#main:0@p2 -> Coordinator#main:1@p0`
6. `Peer#main:1@p2 -> Coordinator#main:0@p0`
7. `Peer#main:1@p2 -> Coordinator#main:1@p0`
8. `Coordinator#main:2@p0 -> Peer#main:2@p1`
9. `Coordinator#main:3@p0 -> Peer#main:2@p2`
10. `Coordinator#main:4@p0 -> Peer#main:2@p1`
11. `Coordinator#main:5@p0 -> Peer#main:2@p2`

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `716, 564` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `948, 78` (dist=0,0625, cov=33,3%)
- `572, 525` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `572, 525` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `948, 78` (dist=0,0625, cov=33,3%)
- `716, 564` (dist=0,0625, cov=33,3%)
- `479, 10` (dist=0,0625, cov=33,3%)
- `230, 695` (dist=0,0625, cov=33,3%)
- `349, 173` (dist=0,0625, cov=33,3%)
- `926, 923` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `926, 923` -> `926, 417` (1 genes alterados)

**CRUZAMENTO** — Pai A `572` + Pai B `929` -> Filho A `929` + Filho B `572` (2 genes trocados)

**CRUZAMENTO** — Pai A `929` + Pai B `716` -> Filho A `716` + Filho B `929` (2 genes trocados)

**CRUZAMENTO** — Pai A `337` + Pai B `695` -> Filho A `695` + Filho B `337` (2 genes trocados)

## Geração 1

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `716, 564` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `929, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 2 | `948, 78` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 3 | `572, 525` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 4 | `948, 78` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `716, 525` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 6 | `572, 695` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `929, 564` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 8 | `479, 10` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 9 | `230, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 10 | `349, 173` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 11 | `926, 417` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `926, 417` (dist=0,0625, cov=33,3%)
- `479, 10` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `929, 564` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `926, 417` (dist=0,0625, cov=33,3%)
- `572, 695` (dist=0,0625, cov=33,3%)
- `929, 564` (dist=0,0625, cov=33,3%)
- `948, 78` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `716, 525` (dist=0,0625, cov=33,3%)
- `572, 525` (dist=0,0625, cov=33,3%)
- `230, 337` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `572, 695` -> `915, 397` (2 genes alterados)

**CRUZAMENTO** — Pai A `397` + Pai B `337` -> Filho A `337` + Filho B `397` (2 genes trocados)

## Geração 2

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `926, 417` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `479, 10` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 2 | `929, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 3 | `929, 564` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 4 | `948, 78` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `572, 525` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 6 | `926, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `915, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 8 | `929, 564` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 9 | `929, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 10 | `716, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 11 | `230, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `716, 525` (dist=0,0625, cov=33,3%)
- `926, 417` (dist=0,0625, cov=33,3%)
- `929, 397` (dist=0,0625, cov=33,3%)
- `915, 337` (dist=0,0625, cov=33,3%)
- `230, 337` (dist=0,0625, cov=33,3%)
- `479, 10` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `572, 525` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `716, 525` -> `221, 525` (1 genes alterados)

**CRUZAMENTO** — Pai A `337` + Pai B `337` -> Filho A `337` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `221` + Pai B `230` -> Filho A `230` + Filho B `221` (2 genes trocados)

**CRUZAMENTO** — Pai A `926` + Pai B `479` -> Filho A `479` + Filho B `926` (2 genes trocados)

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `915, 337` (dist=0,0625, cov=33,3%)
- `926, 417` (dist=0,0625, cov=33,3%)
- `230, 337` (dist=0,0625, cov=33,3%)
- `716, 525` (dist=0,0625, cov=33,3%)

## Geração 3

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `915, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `926, 417` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 2 | `230, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 3 | `716, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 4 | `572, 525` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `230, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 6 | `479, 417` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 7 | `929, 397` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 8 | `915, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 9 | `221, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 10 | `926, 10` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 11 | `929, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `221, 337` (dist=0,0625, cov=33,3%)
- `929, 397` (dist=0,0625, cov=33,3%)
- `915, 337` (dist=0,0625, cov=33,3%)
- `572, 525` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `221, 337` (dist=0,0625, cov=33,3%)
- `929, 397` (dist=0,0625, cov=33,3%)
- `572, 525` (dist=0,0625, cov=33,3%)
- `915, 337` (dist=0,0625, cov=33,3%)
- `716, 525` (dist=0,0625, cov=33,3%)
- `926, 417` (dist=0,0625, cov=33,3%)
- `929, 337` (dist=0,0625, cov=33,3%)
- `479, 417` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `929, 397` -> `430, 397` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `716, 525` -> `716, 681` (1 genes alterados)

**CRUZAMENTO** — Pai A `337` + Pai B `681` -> Filho A `681` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `525` + Pai B `417` -> Filho A `417` + Filho B `525` (2 genes trocados)

**CRUZAMENTO** — Pai A `337` + Pai B `337` -> Filho A `337` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `681` + Pai B `417` -> Filho A `417` + Filho B `681` (2 genes trocados)

**CRUZAMENTO** — Pai A `337` + Pai B `337` -> Filho A `337` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `397` + Pai B `525` -> Filho A `525` + Filho B `397` (2 genes trocados)

## Geração 4

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `221, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `929, 397` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 2 | `915, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 3 | `572, 525` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 4 | `221, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `430, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 6 | `572, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `915, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 8 | `716, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 9 | `926, 681` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 10 | `929, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 11 | `479, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `915, 337` (dist=0,0625, cov=33,3%)
- `479, 397` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `430, 525` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `915, 337` (dist=0,0625, cov=33,3%)
- `479, 397` (dist=0,0625, cov=33,3%)
- `430, 525` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `915, 337` (dist=0,0625, cov=33,3%)
- `929, 397` (dist=0,0625, cov=33,3%)
- `926, 681` (dist=0,0625, cov=33,3%)
- `572, 417` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `915, 337` -> `829, 337` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `929, 397` -> `116, 397` (1 genes alterados)

**CRUZAMENTO** — Pai A `337` + Pai B `397` -> Filho A `397` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `525` + Pai B `417` -> Filho A `417` + Filho B `525` (2 genes trocados)

**CRUZAMENTO** — Pai A `829` + Pai B `716` -> Filho A `716` + Filho B `829` (2 genes trocados)

**CRUZAMENTO** — Pai A `915` + Pai B `116` -> Filho A `116` + Filho B `915` (2 genes trocados)

**CRUZAMENTO** — Pai A `430` + Pai B `915` -> Filho A `915` + Filho B `430` (2 genes trocados)

**CRUZAMENTO** — Pai A `337` + Pai B `681` -> Filho A `681` + Filho B `337` (2 genes trocados)

## Geração 5

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `915, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 1 | `479, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 2 | `716, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 3 | `430, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 4 | `479, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 5 | `716, 397` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 6 | `915, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `829, 681` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 8 | `116, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 9 | `430, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 10 | `926, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 11 | `572, 525` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `926, 337` (dist=0,0625, cov=33,3%)
- `116, 337` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `915, 417` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `915, 337` (dist=0,0625, cov=33,3%)
- `430, 337` (dist=0,0625, cov=33,3%)
- `116, 337` (dist=0,0625, cov=33,3%)
- `479, 397` (dist=0,0625, cov=33,3%)
- `479, 397` (dist=0,0625, cov=33,3%)
- `716, 397` (dist=0,0625, cov=33,3%)
- `915, 417` (dist=0,0625, cov=33,3%)
- `829, 681` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `915, 337` -> `577, 589` (2 genes alterados)

**MUTAÇÃO (Mutator)** — `479, 397` -> `479, 220` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `915, 417` -> `915, 345` (1 genes alterados)

**CRUZAMENTO** — Pai A `589` + Pai B `220` -> Filho A `220` + Filho B `589` (2 genes trocados)

**CRUZAMENTO** — Pai A `116` + Pai B `829` -> Filho A `829` + Filho B `116` (2 genes trocados)

**CRUZAMENTO** — Pai A `430` + Pai B `479` -> Filho A `479` + Filho B `430` (2 genes trocados)

**CRUZAMENTO** — Pai A `577` + Pai B `479` -> Filho A `479` + Filho B `577` (2 genes trocados)

**CRUZAMENTO** — Pai A `589` + Pai B `397` -> Filho A `397` + Filho B `589` (2 genes trocados)

**CRUZAMENTO** — Pai A `397` + Pai B `345` -> Filho A `345` + Filho B `397` (2 genes trocados)

**CRUZAMENTO** — Pai A `479` + Pai B `116` -> Filho A `116` + Filho B `479` (2 genes trocados)

## Geração 6

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `926, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `116, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 2 | `716, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 3 | `915, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 4 | `479, 220` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `116, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 6 | `829, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `430, 345` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 8 | `577, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 9 | `716, 589` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 10 | `915, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 11 | `479, 681` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `479, 681` (dist=0,0625, cov=33,3%)
- `479, 220` (dist=0,0625, cov=33,3%)
- `915, 397` (dist=0,0625, cov=33,3%)
- `926, 337` (dist=0,0625, cov=33,3%)

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `915, 397` (dist=0,0625, cov=33,3%)
- `829, 337` (dist=0,0625, cov=33,3%)
- `915, 417` (dist=0,0625, cov=33,3%)
- `479, 220` (dist=0,0625, cov=33,3%)
- `116, 337` (dist=0,0625, cov=33,3%)
- `479, 681` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `116, 337` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `116, 337` -> `274, 337` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `716, 337` -> `716, 143` (1 genes alterados)

**CRUZAMENTO** — Pai A `397` + Pai B `143` -> Filho A `143` + Filho B `397` (2 genes trocados)

**CRUZAMENTO** — Pai A `829` + Pai B `274` -> Filho A `274` + Filho B `829` (2 genes trocados)

**CRUZAMENTO** — Pai A `417` + Pai B `681` -> Filho A `681` + Filho B `417` (2 genes trocados)

**CRUZAMENTO** — Pai A `681` + Pai B `220` -> Filho A `220` + Filho B `681` (2 genes trocados)

**CRUZAMENTO** — Pai A `220` + Pai B `337` -> Filho A `337` + Filho B `220` (2 genes trocados)

**CRUZAMENTO** — Pai A `915` + Pai B `479` -> Filho A `479` + Filho B `915` (2 genes trocados)

**CRUZAMENTO** — Pai A `274` + Pai B `716` -> Filho A `716` + Filho B `274` (2 genes trocados)

**CRUZAMENTO** — Pai A `915` + Pai B `116` -> Filho A `116` + Filho B `915` (2 genes trocados)

## Geração 7

**Cobertura da POPULAÇÃO (união, o número que todo run real desse projeto reporta)**: 50,0% — elementos únicos cobertos por ALGUÉM: [2, 3, 6, 7, 10, 11]

| # | Genes (TESTDATA) | Distância | Cobertura (deste indivíduo) | Elementos cobertos (deste indivíduo) | Distância por aresta NÃO coberta (índice:valor) |
|---|---|---|---|---|---|
| 0 | `479, 681` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 1 | `479, 220` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 2 | `915, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 3 | `926, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 4 | `479, 143` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 5 | `716, 337` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 6 | `116, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 7 | `479, 681` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 8 | `829, 220` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 9 | `915, 417` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |
| 10 | `274, 397` | 0,0625 | 33,3% | [3, 6, 10, 11] | `0:0,063, 1:0,063, 2:0,000, 4:0,063, 5:0,063, 7:0,000, 8:0,250, 9:0,250` |
| 11 | `915, 337` | 0,0625 | 33,3% | [2, 7, 10, 11] | `0:0,063, 1:0,063, 3:0,000, 4:0,063, 5:0,063, 6:0,000, 8:0,250, 9:0,250` |

**PAIS DA PRÓXIMA GERAÇÃO (offspringSelector)** — pediu 8 de 12 indivíduos, selecionou:

- `274, 397` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `915, 397` (dist=0,0625, cov=33,3%)
- `915, 417` (dist=0,0625, cov=33,3%)
- `116, 337` (dist=0,0625, cov=33,3%)
- `479, 681` (dist=0,0625, cov=33,3%)
- `479, 220` (dist=0,0625, cov=33,3%)
- `479, 681` (dist=0,0625, cov=33,3%)

**MUTAÇÃO (Mutator)** — `915, 397` -> `596, 397` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `479, 681` -> `622, 681` (1 genes alterados)

**MUTAÇÃO (Mutator)** — `479, 220` -> `479, 658` (1 genes alterados)

**CRUZAMENTO** — Pai A `337` + Pai B `681` -> Filho A `681` + Filho B `337` (2 genes trocados)

**CRUZAMENTO** — Pai A `417` + Pai B `337` -> Filho A `337` + Filho B `417` (2 genes trocados)

**CRUZAMENTO** — Pai A `596` + Pai B `479` -> Filho A `479` + Filho B `596` (2 genes trocados)

**SOBREVIVENTES (survivorsSelector)** — pediu 4 de 12 indivíduos, selecionou:

- `915, 397` (dist=0,0625, cov=33,3%)
- `274, 397` (dist=0,0625, cov=33,3%)
- `716, 337` (dist=0,0625, cov=33,3%)
- `479, 681` (dist=0,0625, cov=33,3%)


## Conclusão: por que a distância não empurra a busca pra fora do platô de 50%

Rodando as 7 gerações completas (pop=12), a distância agregada de **todo** indivíduo, em **toda** geração, ficou travada exatamente em `0,0625` — nenhum indivíduo jamais escapou desse valor. A cobertura de população também travou em 50% (`[2, 3, 6, 7, 10, 11]`), e a cobertura de cada indivíduo isolado nunca passou de 33,3% (4 dos 12 elementos). Com o detalhamento por aresta adicionado agora, dá pra mostrar exatamente por quê — com números reais, não especulação.

**Decodificando os 12 elementos exigidos** (Peer tem duas rotas de envio por peer — `main:0` é o ramo QUORUM, `main:1` é o ramo NORMAL — e o Coordinator tem dois slots de recepção — `main:0`/slot0, `main:1`/slot1):

| Elemento | Significado |
|---|---|
| 0, 1 | Peer1 ramo QUORUM → slot0 / slot1 |
| 2, 3 | Peer1 ramo NORMAL → slot0 / slot1 |
| 4, 5 | Peer2 ramo QUORUM → slot0 / slot1 |
| 6, 7 | Peer2 ramo NORMAL → slot0 / slot1 |
| 8, 9 | Coordinator celebra (quorum atingido) → Peer1 / Peer2 |
| 10, 11 | Coordinator rejeita (quorum não atingido) → Peer1 / Peer2 |

Em **todos** os indivíduos das 7 gerações — população inicial, filhos de cruzamento, mutantes, todos — nenhum dos dois genes caiu dentro da janela `[480, 519]` (o valor mais próximo observado foi `479`, a 1 unidade de distância). Ou seja: nenhum Peer jamais tomou o ramo QUORUM em execução nenhuma. Isso já explica metade do platô: os elementos 0, 1, 4, 5, 8, 9 (tudo que depende de pelo menos um Peer entrar na janela) nunca tiveram chance nenhuma de serem cobertos nessa amostra — não é que a distância não ajudou, é que a busca (com só 12 indivíduos × 7 gerações) simplesmente não teve amostra suficiente pra tropeçar na janela.

**A outra metade do platô é o achado importante.** Toda execução com os dois Peers fora da janela cai em um de dois padrões espelhados, dependendo só da ordem de chegada dos pacotes no Coordinator (pura corrida de SO, não controlada pelos genes):

```
| # | Genes         | Distância | Cobertura | Cobertos          | Distância por aresta não coberta |
|---|---------------|-----------|-----------|-------------------|-----------------------------------|
| 0 | `716, 564`    | 0,0625    | 33,3%     | [2, 7, 10, 11]     | 0:0,063 1:0,063 3:0,000 4:0,063 5:0,063 6:0,000 8:0,250 9:0,250 |
| 1 | `926, 417`    | 0,0625    | 33,3%     | [3, 6, 10, 11]     | 0:0,063 1:0,063 2:0,000 4:0,063 5:0,063 7:0,000 8:0,250 9:0,250 |
```

Repare nas arestas 3 e 6 (primeira linha) e 2 e 7 (segunda linha): estão **descobertas**, mas com distância **0,000** — o valor mínimo possível, igual ao de uma aresta já coberta. Isso não é bug de exibição; é o comportamento exato do código, `src/CoverageInst/GraphDistance.java:60-74`:

```java
Set<String> observed = observedNodesByProcess.getOrDefault(processId, Set.of());
...
for (int i = 0; i < path.size(); i++) {
    if (!observed.contains(path.get(i))) { missingCount++; ... }
}
if (missingCount == 0) {
    return 0.0;
}
```

`observedNodesByProcess` guarda, por **processo**, o conjunto de blocos que ele já visitou — sem nenhuma noção de "visitado *por qual mensagem especificamente*". No caso do elemento 3 (Peer1 ramo NORMAL → slot1): Peer1 **executou** o bloco de envio do ramo NORMAL (elemento 2, slot0, foi coberto) e o Coordinator **executou** o bloco de recepção do slot1 (porque foi lá que o pacote do Peer2 caiu, cobrindo o elemento 7). Os dois blocos, sender e receiver, foram fisicamente alcançados nessa execução — só que por mensagens diferentes, não pela combinação específica que define o elemento 3. Como o cálculo só pergunta "esse bloco já foi visto por esse processo alguma vez", ambos os lados dão `missingCount == 0`, e a aresta 3 — tecnicamente descoberta — recebe a mesma distância de uma aresta já coberta: zero.

**Isso confirma, com números reais, exatamente a suspeita que você levantou** — mas de forma mais específica do que "a distância não pensa no código sequencial em geral". A parte sequencial *dentro* de cada processo continua funcionando normalmente: os elementos 0, 1, 4, 5 (ramo QUORUM nunca executado) mostram uma distância real e não-trivial (`0,063`), vinda do `BranchDistance` sobre o valor bruto do gene contra os limites da janela — esse gradiente é genuíno e vai diminuindo conforme o valor se aproxima de `480`/`519`. **O que não tem gradiente nenhum é especificamente a correlação de pareamento entre lados de uma aresta `MESSAGE`** — a "corrida" propriamente dita. Uma vez que os dois blocos (envio e recepção) já foram fisicamente tocados por *algum* evento do processo, a distância não tem como expressar "faltou só a ordem de chegada bater" — ela já está no piso (zero), disfarçada de "praticamente coberta".

**Consequência prática pro platô observado**: com os dois Peers sempre fora da janela, a única coisa que varia de execução pra execução é qual dos dois padrões espelhados (`[2,7,10,11]` vs `[3,6,10,11]`) acontece — decidido por escalonamento do SO/JVM, não pelos genes. Como as arestas 3/6 (ou 2/7, dependendo do padrão) já contam como "distância zero" mesmo descobertas, a soma de distância fica idêntica nos dois casos (`0,0625` — inteiramente carregada pelas 4 arestas do ramo QUORUM, elementos 0,1,4,5, mais o piso fixo de 0,25 das celebrações 8/9, que também nunca têm gradiente por dependerem de uma contagem agregada, não de uma comparação numérica direta). Não existe, portanto, **nenhum sinal de fitness** que diferencie "population parada há 7 gerações no padrão A" de "population parada há 7 gerações no padrão B" — e nenhum sinal que diga "está quase alcançando o pareamento certo". A única saída do platô, nesse cenário, é ou (a) sorte de escalonamento repetida o suficiente pra também cobrir o padrão espelhado (o que já aconteceria por acaso com mais execuções por indivíduo), ou (b) um gene efetivamente cair dentro de `[480, 519]`, ativando o gradiente real do `BranchDistance` que empurra os elementos 0/1/4/5/8/9.

**Achado a registrar em `research_questions.md`**: o cálculo de distância do CoverageInst dá gradiente real para decisões *numéricas dentro de um processo* (branch distance clássico), mas é estruturalmente cego ao *pareamento de mensagens* em si — o núcleo do que uma aresta `MESSAGE` deveria medir. Uma aresta descoberta por "faltou a ordem de chegada bater" e uma aresta genuinamente coberta são indistinguíveis pela métrica (ambas retornam 0,0 de `sideDistance` quando os dois blocos endpoint já foram tocados). Isso é diferente do achado anterior desta sessão (`IDENTITY`-kind é cego a ordem por design) — aqui o problema aparece mesmo no kind `MESSAGE`, que é justamente o único desenhado pra representar corrida de verdade.

## Atualização: implementado `chainedDistance` (cross-processo + local) e correção de um erro deste documento

Depois da conclusão acima, implementamos de verdade o mecanismo de "distância encadeada" discutido com o usuário (ver `research_questions.md` e a memória do projeto para o desenho completo). No caminho, descobrimos que **a explicação da seção anterior sobre os elementos 0/1/4/5 estava parcialmente errada** - registrando aqui a correção, com números reais.

**O erro**: eu disse que a distância `0,063` (= `0,0625` arredondado) dos elementos 0/1/4/5 vinha de "um gradiente real e não-trivial... vindo do `BranchDistance` sobre o valor bruto do gene contra os limites da janela". **Isso não era verdade.** Rastreando o ponto de divergência exato (via `RequiredElementsMain`-style dump de `branchPredicates`/`syncEdgeBlocks` para `Peer#main`), o bloco realmente "faltando" no caminho até o envio QUORUM não é a comparação numérica (`value>=480`, `value<=519`) - é o **consumo do booleano `inWindow` já calculado**, via `if (inWindow)` (bytecode `IFEQ` sobre um valor 0/1). Exatamente o mesmo "flag problem" do `.equals()` do Coordinator, só que dentro do próprio `Peer.java`. `BranchDistance.compute(IFEQ, 0, 0, wantedTaken=false)` dá **sempre exatamente `0,5`**, não importa se o valor bruto é `479` (1 de distância) ou `12` (468 de distância) - o que já estava provado pelos próprios dados da sessão (o mesmo `0,063` aparecendo idêntico pra `716, 572, 929, 479, 915...`), só que eu interpretei errado na hora.

**A correção**: `GraphDistance.ChainedSource` agora aceita, além da forma cross-processo já implementada, uma forma **local** (`localBlock` + `wantedTaken`) que aponta diretamente pro(s) bloco(s) que REALMENTE calculam o booleano - descobertos via dump direto (`Peer#main:B0` = `IF_ICMPLT value, 480`, `Peer#main:B1` = `IF_ICMPGT value, 519`, ambos sempre executados incondicionalmente antes do `if`, exceto quando o curto-circuito pula `B1`). Declarado em `config/quorum-handshake-covinst.json`:

```json
"Peer#main:0": [
  { "localBlock": "Peer#main:B1", "wantedTaken": false },
  { "localBlock": "Peer#main:B0", "wantedTaken": false }
]
```

**Resultado, com números reais do mesmo pop=12/7-gerações**: os elementos 0/1/4/5 agora mostram valores **diferentes entre indivíduos diferentes**, refletindo a distância real até a janela - `309, 468`: elemento 4 (peer2=468, a 12 de distância de 480) = `0,116`; elemento 0 (peer1=309, a 171 de distância) = `0,124`. Antes, TODOS os indivíduos da sessão inteira mostravam exatamente `0,063`/`0,0625`, sem exceção, independente do valor. A distância agregada por indivíduo também deixou de ser um platô fixo em `0,0625` e passou a variar (`0,0807`-`0,0832` nesta amostra) - um sinal de fitness genuinamente mais informativo do que existia antes.

**Lição prática**: `chainedDistance` precisou virar uma prioridade explícita sobre o predicado local (não só um fallback), porque `IFEQ`/`IFNE` SÃO reconhecidos pelo `BranchDistance` como predicados numéricos válidos - sem essa prioridade, o "predicado local" (o flag) "resolvia com sucesso" silenciosamente, mascarando exatamente o problema que o mecanismo existe pra resolver. Suíte completa (119/119, +4 testes novos) segue passando; nenhum dos outros ~20 benchmarks já migrados é afetado (mecanismo opt-in por aresta).
