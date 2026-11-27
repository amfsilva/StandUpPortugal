# StandUp Portugal — Guia de Gestão e Desenvolvimentos

**Última atualização:** 2026-11-25  
**Repositório GitHub:** https://github.com/amfsilva/StandUpPortugal  
**Feed JSON público (GitHub Pages):** https://amfsilva.github.io/StandUpPortugal/events.json  
**Feed Online público (Especiais):** https://amfsilva.github.io/StandUpPortugal/online_specials.json  
**Política de Privacidade (Google Play):** https://amfsilva.github.io/StandUpPortugal/privacy.html  
**Formulário de Sugestões / Bugs:** https://docs.google.com/forms/d/e/1FAIpQLScao4UEvx5vsajCHUrdAh_4uZlxPHTAAL_uIMxldyFBC4whrg/viewform  
**Google Sheet de Eventos e Destaques:** https://docs.google.com/spreadsheets/d/1Of0g9oUzgiO8OLR0zTWlKeioWC2-HKwEoz7vcpFrNdk/edit  

---

## 1. Como funciona a arquitetura (100% sem servidor)

- **App Android (`pt.standupportugal.comedy`):** Desenvolvida em Kotlin + Jetpack Compose. Lê o ficheiro `events.json` no GitHub Pages, remove eventos passados, ordena por data crescente (mais próximos primeiro), permite filtrar por **Distrito** e abre o link oficial de compra/evento ao tocar num cartão.
- **Recolha automática (`scraper/fetch_events.py`):** Corre automaticamente no GitHub Actions de **6 em 6 horas** (e sempre que o scraper é alterado). Recolhe espetáculos públicos de **BOL**, **Eventbrite**, **Meetup** e **Ticketline**, junta os eventos manuais da **Google Sheet** e grava `docs/events.json`.
- **Compilação na nuvem (`.github/workflows/build-android.yml`):** Sempre que há alterações no código, compila na nuvem o `.apk` (teste direto) e o `.aab` assinado (Google Play), sem precisar de Android Studio nem SDKs no PC.

---

## 2. Como gerir eventos, moderação e fontes na Google Sheet

Abra a folha **StandUp Portugal - Eventos** no Google Drive (`myAntigravity > Projecto StandUp Portugal`). Ela tem **3 abas**:

### 2.1 Aba `Folha1` — Adicionar, Destacar ou Ocultar Espetáculos
Cada linha representa um espetáculo manual ou uma regra sobre um espetáculo existente:
- `title`: Nome do espetáculo / comediante
- `dateTime`: Data e hora em formato ISO-8601 (ex.: `2026-12-12T21:30:00+00:00`)
- `venue`: Nome da sala/bar
- `district`: Distrito (ex.: `Lisboa`, `Porto`, `Coimbra`, `Braga`, `Faro`, `Açores`, etc.)
- `imageUrl`: Link direto da imagem/cartaz (opcional)
- `ticketUrl`: Link oficial de compra ou evento *(chave única usada para identificar o evento)*
- `source`: Nome da fonte (ex.: `Facebook`, `Meetup`, `BOL`)
- `featured`: `TRUE` para destacar com borda dourada e selo **Em Destaque**, ou `FALSE` para normal.
- `hide`: `TRUE` para **ocultar/remover** o espetáculo do feed; `FALSE` (ou vazio) para mostrar.

**Exemplos práticos:**
- **Adicionar evento do Facebook / bar local:** preencha uma nova linha com todos os campos e `hide = FALSE`.
- **Promover um evento que já vem do scraper:** copie o `ticketUrl` da aba `Feed Atual`, cole numa nova linha da `Folha1`, preencha os campos e coloque `featured = TRUE`.
- **Remover/esconder um evento errado do scraper:** copie o `ticketUrl` da aba `Feed Atual`, cole numa nova linha da `Folha1` e coloque `hide = TRUE` (os restantes campos podem ficar simples ou copiados).

### 2.2 Aba `Fontes` — Adicionar ou Desativar Sites/Locais Automáticos
Contém os sites públicos com dados estruturados (`JSON-LD`) que o scraper consulta:
- `name`: Nome da fonte/local que aparece no cartão da app (ex.: `Teatro Sá da Bandeira`)
- `url`: Endereço da página de agenda pública
- `defaultDistrict`: Distrito por defeito caso a página não indique cidade (ex.: `Porto`, `Lisboa`)
- `sourceType`: `JSON-LD`
- `active`: `TRUE` para recolher automaticamente; `FALSE` para pausar/desativar essa fonte sem apagar a linha.

### 2.3 Aba `Feed Atual` — Espelho dos Eventos no Ar
Lista os espetáculos atualmente ativos no `events.json`, para consultar rapidamente o que está visível na app e copiar o `ticketUrl` exato quando quiser destacar ou ocultar algum evento na `Folha1`.

### 2.4 Aba `Online` — Especiais Completos em Português (PT e PT-BR)
Permite gerir e priorizar o separador **Online (Especiais)** da app (apenas espetáculos em português de Portugal ou do Brasil — entradas sem `PT` ou `BR` em `language` são ignoradas).

**Regra de curadoria rigorosa (apenas fontes oficiais):**
- **YouTube:** aceitar apenas espetáculos publicados no **canal oficial do próprio humorista ou produtora** (nunca re-uploads de terceiros).
- **Streaming:** apenas links diretos oficiais de **Netflix**, **Max (HBO)**, **Prime Video** ou **RTP Play**.

Colunas:
- `title`, `comedian`, `platform` (`YouTube`, `Netflix`, `HBO`, `Prime Video`, `RTP Play`), `language` (`PT` ou `PT-BR`), `publishedDate` (`YYYY-MM-DD`), `year`, `imageUrl`, `watchUrl`
- `featured`: `TRUE` para destacar no topo com selo **Em Destaque** (os destacados ordenam-se por `priority` decrescente; os restantes aparecem primeiro `PT` e depois `PT-BR`, sempre do mais recente para o mais antigo por `publishedDate`)
- `priority`: número inteiro (ex.: `100`, `50`, `10`) para desempate
- `hide`: `TRUE` para ocultar algum item

**Prompt para o Gemini no telemóvel:** consulte [GEMINI_TELEMOVEL_PROMPT.md](GEMINI_TELEMOVEL_PROMPT.md) para pedir novos eventos/especiais ao Gemini diretamente no telemóvel.

### 2.5 Configuração única (Publicar na Web) e Atualização Imediata
1. Na Google Sheet: **Ficheiro → Partilhar → Publicar na Web**.
2. Publique como **Valores separados por vírgulas (.csv)**:
   - A aba **`Folha1`** → guarde o URL no secret **`GOOGLE_SHEET_CSV_URL`** do GitHub (**Settings → Secrets and variables → Actions**).
   - A aba **`Fontes`** → guarde o URL no secret **`GOOGLE_SHEET_SOURCES_CSV_URL`** do GitHub.
   - A aba **`Online`** → guarde o URL no secret **`GOOGLE_SHEET_ONLINE_CSV_URL`** do GitHub.
3. **Quando entra em vigor?** Automaticamente de **6 em 6 horas** (ou em ~15 segundos se abrir **Actions → Update Stand-Up Events Feed → Run workflow** no GitHub). **Não é preciso lançar nova versão da app na Google Play.**

---

## 3. Como compilar e atualizar a app na Google Play

1. **Compilação automática:** Qualquer alteração enviada para o branch `main` dispara o workflow **Build Android App (APK & AAB)** no GitHub Actions.
2. **Descarregar ficheiros:** Em **Actions**, abra a última execução verde e desça até **Artifacts**:
   - `StandUpPortugal-debug-apk` → para instalar diretamente no telemóvel.
   - `StandUpPortugal-release-aab` → para carregar na **Google Play Console**.
3. **Secrets de assinatura configurados no GitHub:**
   - `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
   - Guarde o ficheiro `standup-release.jks` em segurança na sua pasta `StandupPortugal` do Google Drive.
4. **Atualizar na Play Console:** Antes de gerar um novo `.aab` para a Play Store, incremente o `versionCode` e `versionName` em `app/build.gradle.kts`.
5. **Deploy automático para Testes Internos (opcional):** Se configurar o secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` no GitHub (com a chave JSON de uma *Service Account* da Google Cloud ligada à Play Console com permissão de *Gerir versões* / *Release to testing tracks*), cada alteração na pasta `app/` enviada para `main` envia automaticamente o `.aab` para a faixa **Testes internos**!

---

## 4. Histórico de Desenvolvimentos

| Data | Versão / Commit | O que foi feito |
| :--- | :--- | :--- |
| 2026-11-25 | `v1.0.0` (`b2cbba7`) | Criação inicial da app Android (Kotlin/Compose), scraper Python, GitHub Pages e workflows de build/atualização na cloud. |
| 2026-11-25 | `329cec9` / `4d33150` | Adicionados Eventbrite e Meetup (JSON-LD), filtros de relevância/distritos, keystore na cloud (`generate-keystore.yml`) e `.trim()` nos secrets de assinatura. |
| 2026-11-25 | `v1.0.1` (`07b0af1`) | Atualizado `applicationId` para `pt.standupportugal.comedy`, `compileSdk`/`targetSdk` para **API 36** (exigência Google Play) e `versionCode = 2`. |
| 2026-11-25 | Google Sheet & Doc | Criada a Google Sheet `StandUp Portugal - Eventos` e este documento de gestão no Google Drive. |
| 2026-11-25 | `privacy.html` | Publicada a Política de Privacidade no GitHub Pages (`docs/privacy.html`) para a Google Play Console. |
| 2026-11-25 | `v1.1.0` | Adicionada pesquisa por comediante/sala, botão de partilha de espetáculos, textos da loja (`docs/GOOGLE_PLAY_FICHA.md`) e imagens `512x512` + `1024x500` em `store-assets/`. |
| 2026-11-25 | `v1.1.1` | Adicionado aviso de isenção de responsabilidade na app (botão `ⓘ` + rodapé), em `docs/privacy.html` e na descrição da Google Play. |
| 2026-11-26 | Sheet Moderação & Fontes | Adicionada coluna `hide` para ocultar eventos, aba `Fontes` (`GOOGLE_SHEET_SOURCES_CSV_URL`) para gerir sites/bares no Drive e aba `Feed Atual` com os 174 eventos ativos. |
| 2026-11-26 | `v1.2.0` | Adicionado alternador **Ao Vivo / Online (Especiais)** na app, filtro por plataforma, `docs/online_specials.json` e aba `Online` na Google Sheet com `featured`, `priority` e `hide`. |
| 2026-11-26 | Auto-Deploy Play | Configurado envio automático opcional do `.aab` para **Testes internos** da Google Play via `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (`r0adkll/upload-google-play`). |
| 2026-11-27 | `v1.3.0` | Corrigido distrito do Diogo Faro (Santarém), removido e-mail pessoal dos docs, adicionado formulário de contacto (`contacto.html`), `Max`→`HBO`, data de publicação e ordenação por mais recente no Online, incluído Vítor Sá e guia Gemini para telemóvel. |
| 2026-11-27 | `v1.3.1` | Ordenação do Online atualizada: destacados por ordem de `priority` no topo, seguidos de `PT` (mais recentes primeiro) e depois `PT-BR` (mais recentes primeiro). |
| 2026-11-27 | `v1.3.2` | Criado Google Form oficial (2 campos) ligado à Google Sheet e notificações por e-mail, e ligado diretamente na app (`FEEDBACK_FORM_URL`) e em `privacy.html` / `contacto.html`. |
| 2026-11-27 | `v1.3.3` | Renomeado o separador `Online (Especiais)` para `Online`. |

---

## 5. Próximos Desenvolvimentos (Backlog)

- **Sugerir novo espaço de comédia por e-mail / formulário:** Adicionar ligação direta ao formulário oficial de sugestões, destaques e bugs no diálogo `ⓘ`.
