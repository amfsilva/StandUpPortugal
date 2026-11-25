# StandUp Portugal — Guia de Gestão e Desenvolvimentos

**Última atualização:** 2026-11-25  
**Repositório GitHub:** https://github.com/amfsilva/StandUpPortugal  
**Feed JSON público (GitHub Pages):** https://amfsilva.github.io/StandUpPortugal/events.json  
**Google Sheet de Eventos e Destaques:** https://docs.google.com/spreadsheets/d/1Of0g9oUzgiO8OLR0zTWlKeioWC2-HKwEoz7vcpFrNdk/edit  

---

## 1. Como funciona a arquitetura (100% sem servidor)

- **App Android (`pt.standupportugal.comedy`):** Desenvolvida em Kotlin + Jetpack Compose. Lê o ficheiro `events.json` no GitHub Pages, remove eventos passados, ordena por data crescente (mais próximos primeiro), permite filtrar por **Distrito** e abre o link oficial de compra/evento ao tocar num cartão.
- **Recolha automática (`scraper/fetch_events.py`):** Corre automaticamente no GitHub Actions de **6 em 6 horas** (e sempre que o scraper é alterado). Recolhe espetáculos públicos de **BOL**, **Eventbrite**, **Meetup** e **Ticketline**, junta os eventos manuais da **Google Sheet** e grava `docs/events.json`.
- **Compilação na nuvem (`.github/workflows/build-android.yml`):** Sempre que há alterações no código, compila na nuvem o `.apk` (teste direto) e o `.aab` assinado (Google Play), sem precisar de Android Studio nem SDKs no PC.

---

## 2. Como gerir eventos e destaques no dia a dia

1. Abra a folha **StandUp Portugal - Eventos** no Google Sheets.
2. Para **adicionar um espetáculo manual** (ex.: eventos do Facebook/Instagram ou bares locais), preencha uma nova linha com:
   - `title`: Nome do espetáculo / comediante
   - `dateTime`: Data e hora em formato ISO-8601 (ex.: `2026-12-12T21:30:00+00:00`)
   - `venue`: Nome da sala/bar
   - `district`: Distrito (ex.: `Lisboa`, `Porto`, `Coimbra`, `Braga`, `Faro`, `Açores`, etc.)
   - `imageUrl`: Link direto da imagem/cartaz (opcional)
   - `ticketUrl`: Link oficial de compra ou evento
   - `source`: Nome da fonte (ex.: `Facebook`, `Meetup`, `BOL`)
   - `featured`: `TRUE` para destacar com borda dourada e selo **Em Destaque**, ou `FALSE` para normal.
3. Para **promover um espetáculo já existente** no feed automático, basta adicionar uma linha na folha com o **mesmo `ticketUrl`** e `featured = TRUE` — a entrada da Google Sheet sobrepõe-se à automática.
4. *(Configuração inicial única na Google Sheet)*: **Ficheiro → Partilhar → Publicar na Web → Valores separados por vírgulas (.csv)** e guardar o link no secret `GOOGLE_SHEET_CSV_URL` do GitHub.

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

---

## 4. Histórico de Desenvolvimentos

| Data | Versão / Commit | O que foi feito |
| :--- | :--- | :--- |
| 2026-11-25 | `v1.0.0` (`b2cbba7`) | Criação inicial da app Android (Kotlin/Compose), scraper Python, GitHub Pages e workflows de build/atualização na cloud. |
| 2026-11-25 | `329cec9` / `4d33150` | Adicionados Eventbrite e Meetup (JSON-LD), filtros de relevância/distritos, keystore na cloud (`generate-keystore.yml`) e `.trim()` nos secrets de assinatura. |
| 2026-11-25 | `v1.0.1` (`07b0af1`) | Atualizado `applicationId` para `pt.standupportugal.comedy`, `compileSdk`/`targetSdk` para **API 36** (exigência Google Play) e `versionCode = 2`. |
| 2026-11-25 | Google Sheet & Doc | Criada a Google Sheet `StandUp Portugal - Eventos` e este documento de gestão no Google Drive. |
