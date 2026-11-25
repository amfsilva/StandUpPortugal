# StandUp Portugal

App Android (Kotlin + Jetpack Compose) que agrega espetáculos de **Stand-Up Comedy em Portugal**, ordenados por data mais próxima primeiro, com filtro por **Distrito** e ligação direta ao site oficial de compra.

Tudo funciona **100% sem servidor dedicado**, usando **GitHub Actions + GitHub Pages** (e opcionalmente uma **Google Sheet** para destaques ou eventos manuais do Facebook).

---

## Estrutura do Projeto

- `app/` — Aplicação Android (`pt.standupportugal.app`)
- `scraper/fetch_events.py` — Script Python que recolhe eventos públicos (Ticketline, BOL, etc.), junta eventos/destaques da Google Sheet e gera `docs/events.json`
- `docs/events.json` — Feed JSON servido gratuitamente via GitHub Pages
- `.github/workflows/build-android.yml` — Compila automaticamente na nuvem o `.apk` (teste) e o `.aab` (Google Play)
- `.github/workflows/update-events.yml` — Atualiza `docs/events.json` automaticamente de 6 em 6 horas

---

## Como colocar tudo a funcionar na nuvem (passo a passo)

### 1. Criar repositório no GitHub
1. Crie um repositório no GitHub chamado `StandUpPortugal` (público ou privado; para GitHub Pages gratuito use repositório público).
2. Envie esta pasta `StandUpPortugal/` para o repositório (`git init`, `git add .`, `git commit -m "init"`, `git push`).

### 2. Ativar o GitHub Pages (feed gratuito sem servidor)
1. No GitHub: **Settings → Pages → Build and deployment → Source: Deploy from a branch**.
2. Selecione o branch `main` e a pasta `/docs`.
3. O feed ficará disponível em: `https://<SEU-UTILIZADOR>.github.io/StandUpPortugal/events.json`.
4. Atualize `EVENTS_FEED_URL` em [app/build.gradle.kts](app/build.gradle.kts) com o seu URL.

### 3. Ligar Google Sheet (opcional, para promover espetáculos ou adicionar eventos do Facebook)
1. Crie uma Google Sheet com estas colunas na primeira linha:
   `title,dateTime,venue,district,imageUrl,ticketUrl,source,featured`
   - Exemplo de `dateTime`: `2026-12-05T21:30:00+00:00`
   - Para dar destaque a um espetáculo, coloque `TRUE` na coluna `featured`.
2. Vá a **Ficheiro → Partilhar → Publicar na Web**, escolha **Valores separados por vírgulas (.csv)** e copie o link.
3. No GitHub (**Settings → Secrets and variables → Actions**), crie o secret `GOOGLE_SHEET_CSV_URL` com esse link (ou coloque em `GOOGLE_SHEET_CSV_URL` no `app/build.gradle.kts` para a app ler diretamente).

### 4. Compilar remotamente e publicar na Google Play
1. Cada push para `main` (ou execução manual em **Actions → Build Android App**) gera dois ficheiros para download nos **Artifacts**:
   - `StandUpPortugal-debug-apk` (para instalar e testar no seu Android)
   - `StandUpPortugal-release-aab` (para carregar na Google Play Console)
2. Para o `.aab` sair **assinado para a Google Play**, adicione estes 4 Secrets no GitHub (**Settings → Secrets and variables → Actions**):
   - `RELEASE_KEYSTORE_BASE64` (ficheiro `.jks` convertido para Base64)
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
