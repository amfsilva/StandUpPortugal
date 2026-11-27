"""
StandUp Portugal - Automated Event Aggregator
Collects stand-up comedy events in Portugal from public sources (Eventbrite,
Meetup, Ticketline, BOL) and an optional Google Sheet CSV (for manual
Facebook/bar events and promoted highlights), deduplicates, removes past
events, sorts ascending by date, and writes docs/events.json.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from dateutil import parser as date_parser

LISBON_TZ = timezone(timedelta(hours=0))
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "pt-PT,pt;q=0.9,en;q=0.8",
}

DISTRICT_KEYWORDS: dict[str, list[str]] = {
    "Lisboa": ["lisboa", "lisbon", "cascais", "oeiras", "sintra", "amadora", "loures", "odivelas", "mafra", "torres vedras"],
    "Porto": ["porto", "matosinhos", "gaia", "vila nova de gaia", "maia", "gondomar", "valongo", "póvoa de varzim"],
    "Braga": ["braga", "guimarães", "famalicão", "barcelos", "esposende"],
    "Coimbra": ["coimbra", "figueira da foz", "cantanhede"],
    "Faro": ["faro", "algarve", "portimão", "albufeira", "lagos", "loulé", "tavira", "olhão"],
    "Aveiro": ["aveiro", "ílhavo", "santa maria da feira", "ovar", "águeda", "espinho"],
    "Setúbal": ["setúbal", "almada", "seixal", "barreiro", "montijo", "palmela", "sesimbra", "sines"],
    "Leiria": ["leiria", "caldas da rainha", "marinha grande", "alcobaça", "pombal"],
    "Santarém": ["santarém", "tomar", "torres novas", "abrantes", "entroncamento"],
    "Viseu": ["viseu", "lamego", "tondela"],
    "Viana do Castelo": ["viana do castelo", "ponte de lima", "valença"],
    "Vila Real": ["vila real", "chaves", "peso da régua"],
    "Bragança": ["bragança", "mirandela"],
    "Castelo Branco": ["castelo branco", "covilhã", "fundão"],
    "Guarda": ["guarda", "seia"],
    "Évora": ["évora", "estremoz", "montemor-o-novo"],
    "Beja": ["beja", "moura", "odemira"],
    "Portalegre": ["portalegre", "elvas", "ponte de sor"],
    "Madeira": ["funchal", "madeira", "câmara de lobos"],
    "Açores": ["ponta delgada", "açores", "angra do heroísmo", "horta", "teatro micaelense"],
}

NON_COMEDY_KEYWORDS = (
    "dinner with strangers",
    "quiz & beer",
    "quiz night",
    "language exchange",
    "after-work drinks",
)

OUTSIDE_PORTUGAL_KEYWORDS = (
    "madrid",
    "teatro bellas artes",
)


def is_relevant_portugal_comedy(title: str, venue: str, source: str) -> bool:
    combined = f"{title} {venue}".lower()
    if any(bad in combined for bad in OUTSIDE_PORTUGAL_KEYWORDS):
        return False
    if source == "Meetup" and any(bad in combined for bad in NON_COMEDY_KEYWORDS):
        return False
    return True


def infer_district(text: str, venue_hint: str = "") -> str:
    """
    Infers the Portuguese district. Prioritizes venue/city text over title so
    comedian names (e.g. 'Diogo Faro') never override the actual venue district.
    """
    def _match_district(blob: str) -> str | None:
        cleaned = re.sub(r"\bdiogo\s+faro\b", " ", blob.lower())
        for district, keywords in DISTRICT_KEYWORDS.items():
            for kw in keywords:
                if re.search(rf"\b{re.escape(kw)}\b", cleaned):
                    return district
        if "tivoli" in cleaned or "coliseu dos recreios" in cleaned or "são jorge" in cleaned:
            return "Lisboa"
        if "sá da bandeira" in cleaned or "coliseu porto" in cleaned:
            return "Porto"
        if "teatro virgínia" in cleaned or "teatro virginia" in cleaned:
            return "Santarém"
        return None

    if venue_hint:
        matched = _match_district(venue_hint)
        if matched:
            return matched
    return _match_district(text) or "Portugal"


def make_id(source: str, ticket_url: str, date_str: str) -> str:
    digest = hashlib.sha1(f"{source}|{ticket_url}|{date_str}".encode("utf-8")).hexdigest()[:12]
    return f"{source.lower()}-{digest}"


def parse_iso_datetime(raw: str) -> datetime | None:
    if not raw:
        return None
    cleaned = raw.strip()
    try:
        dt = datetime.fromisoformat(cleaned.replace("Z", "+00:00"))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=LISBON_TZ)
        return dt
    except ValueError:
        pass
    try:
        dt = date_parser.parse(cleaned, dayfirst=True)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=LISBON_TZ)
        return dt
    except (ValueError, TypeError):
        return None


def extract_json_ld_events(soup: BeautifulSoup, source: str, default_base_url: str) -> list[dict[str, Any]]:
    """Extracts schema.org Event items from <script type='application/ld+json'> tags."""
    events: list[dict[str, Any]] = []
    for script in soup.select('script[type="application/ld+json"]'):
        raw_json = script.string or script.get_text()
        if not raw_json:
            continue
        try:
            data = json.loads(raw_json)
        except json.JSONDecodeError:
            continue

        candidates: list[Any] = []
        if isinstance(data, list):
            candidates = data
        elif isinstance(data, dict):
            if "@graph" in data and isinstance(data["@graph"], list):
                candidates = data["@graph"]
            elif "itemListElement" in data and isinstance(data["itemListElement"], list):
                for el in data["itemListElement"]:
                    if isinstance(el, dict):
                        candidates.append(el.get("item", el))
            else:
                candidates = [data]

        for item in candidates:
            if not isinstance(item, dict):
                continue
            item_type = str(item.get("@type", "")).lower()
            if "event" not in item_type:
                continue

            title = str(item.get("name", "")).strip()
            start_raw = str(item.get("startDate", "")).strip()
            dt = parse_iso_datetime(start_raw)
            ticket_url = str(item.get("url", "")).strip()
            if ticket_url:
                ticket_url = urljoin(default_base_url, ticket_url)
            if not title or not dt or not ticket_url:
                continue

            image_val = item.get("image", "")
            if isinstance(image_val, list) and image_val:
                image_url = str(image_val[0])
            elif isinstance(image_val, dict):
                image_url = str(image_val.get("url", ""))
            else:
                image_url = str(image_val or "")
            if image_url.startswith("/"):
                image_url = urljoin(default_base_url, image_url)

            loc = item.get("location") or {}
            venue = "Local a anunciar"
            city_hint = ""
            if isinstance(loc, dict):
                venue = str(loc.get("name") or venue).strip()
                addr = loc.get("address") or {}
                if isinstance(addr, dict):
                    city_hint = f"{addr.get('addressLocality', '')} {addr.get('addressRegion', '')}"
                elif isinstance(addr, str):
                    city_hint = addr

            district = infer_district(f"{venue} {city_hint} {title}", venue_hint=f"{venue} {city_hint}")
            events.append({
                "id": make_id(source, ticket_url, dt.isoformat()),
                "title": title,
                "dateTime": dt.isoformat(),
                "venue": venue,
                "district": district,
                "imageUrl": image_url,
                "ticketUrl": ticket_url,
                "source": source,
                "featured": False,
            })
    return events


def fetch_google_sheet_csv(csv_url: str) -> tuple[list[dict[str, Any]], set[str]]:
    if not csv_url:
        return [], set()
    events: list[dict[str, Any]] = []
    hidden_urls: set[str] = set()
    resp = requests.get(csv_url, headers=HEADERS, timeout=20)
    resp.raise_for_status()
    reader = csv.DictReader(io.StringIO(resp.text))
    for row in reader:
        ticket_url = (row.get("ticketUrl") or "").strip()
        hide_raw = (row.get("hide") or row.get("ocultar") or "").strip().lower()
        if ticket_url and hide_raw in {"true", "1", "sim", "yes"}:
            hidden_urls.add(ticket_url)
            continue

        title = (row.get("title") or "").strip()
        raw_dt = (row.get("dateTime") or "").strip()
        if not title or not raw_dt or not ticket_url:
            continue
        dt = parse_iso_datetime(raw_dt)
        if not dt:
            continue
        venue = (row.get("venue") or "").strip() or "Local a anunciar"
        district = (row.get("district") or "").strip() or infer_district(f"{venue} {title}")
        source = (row.get("source") or "").strip() or "Curadoria"
        featured_raw = (row.get("featured") or "").strip().lower()
        events.append({
            "id": make_id(source, ticket_url, dt.isoformat()),
            "title": title,
            "dateTime": dt.isoformat(),
            "venue": venue,
            "district": district,
            "imageUrl": (row.get("imageUrl") or "").strip(),
            "ticketUrl": ticket_url,
            "source": source,
            "featured": featured_raw in {"true", "1", "sim", "yes"},
        })
    return events, hidden_urls


def fetch_custom_json_ld_sources(sources_csv_url: str) -> list[dict[str, Any]]:
    """Reads custom JSON-LD sources from the 'Fontes' Google Sheet CSV."""
    if not sources_csv_url:
        return []
    events: list[dict[str, Any]] = []
    resp = requests.get(sources_csv_url, headers=HEADERS, timeout=20)
    resp.raise_for_status()
    reader = csv.DictReader(io.StringIO(resp.text))
    for row in reader:
        active_raw = (row.get("active") or row.get("ativo") or "true").strip().lower()
        if active_raw in {"false", "0", "nao", "não", "no"}:
            continue
        name = (row.get("name") or row.get("nome") or "Web").strip()
        url = (row.get("url") or "").strip()
        default_district = (row.get("defaultDistrict") or row.get("distrito_padrao") or "Portugal").strip()
        if not url:
            continue
        try:
            page = requests.get(url, headers=HEADERS, timeout=20)
            print(f"[CustomSource:{name}] {url} -> HTTP {page.status_code}")
            if page.status_code != 200:
                continue
            soup = BeautifulSoup(page.text, "html.parser")
            extracted = extract_json_ld_events(soup, name, url)
            for ev in extracted:
                if ev.get("district") == "Portugal" and default_district:
                    ev["district"] = default_district
            events.extend(extracted)
        except Exception as exc:
            print(f"[CustomSource:{name}] skipped due to error: {exc}")
    return events


def fetch_eventbrite_standup() -> list[dict[str, Any]]:
    urls = [
        "https://www.eventbrite.pt/d/portugal/stand-up-comedy/",
        "https://www.eventbrite.pt/d/portugal--lisbon/comedy/",
        "https://www.eventbrite.pt/d/portugal--porto/comedy/",
    ]
    events: list[dict[str, Any]] = []
    for url in urls:
        resp = requests.get(url, headers=HEADERS, timeout=20)
        print(f"[Eventbrite] {url} -> HTTP {resp.status_code}")
        if resp.status_code != 200:
            continue
        soup = BeautifulSoup(resp.text, "html.parser")
        events.extend(extract_json_ld_events(soup, "Eventbrite", "https://www.eventbrite.pt"))
    return events


def fetch_meetup_standup() -> list[dict[str, Any]]:
    urls = [
        "https://www.meetup.com/find/?keywords=stand%20up%20comedy&location=pt--Lisbon&source=EVENTS",
        "https://www.meetup.com/find/?keywords=comedy&location=pt--Porto&source=EVENTS",
    ]
    events: list[dict[str, Any]] = []
    for url in urls:
        resp = requests.get(url, headers=HEADERS, timeout=20)
        print(f"[Meetup] {url} -> HTTP {resp.status_code}")
        if resp.status_code != 200:
            continue
        soup = BeautifulSoup(resp.text, "html.parser")
        events.extend(extract_json_ld_events(soup, "Meetup", "https://www.meetup.com"))
    return events


def fetch_ticketline_standup() -> list[dict[str, Any]]:
    urls = [
        "https://www.ticketline.pt/pesquisa?q=stand+up",
        "https://www.ticketline.pt/pesquisa?q=stand-up",
        "https://www.ticketline.pt/pesquisa?q=comedy",
        "https://www.ticketline.pt/pesquisa?q=porto+comedy+room",
        "https://www.ticketline.pt/pesquisa?q=pinguim",
        "https://www.ticketline.pt/pesquisa?q=chiado+comedy+club",
        "https://www.ticketline.pt/pesquisa?q=maxime+comedy+club",
    ]
    events: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    non_comedy_slugs = (
        "museu-", "ecoteca-", "igreja-", "dino-parque", "palacio-",
        "exposicao-", "fado-", "world-press-cartoon", "spiritus",
    )

    for url in urls:
        resp = requests.get(url, headers=HEADERS, timeout=20)
        print(f"[Ticketline] {url} -> HTTP {resp.status_code}")
        if resp.status_code != 200:
            continue
        soup = BeautifulSoup(resp.text, "html.parser")
        events.extend(extract_json_ld_events(soup, "Ticketline", "https://www.ticketline.pt"))

        for li in soup.select('li[itemtype*="schema.org/Event"]'):
            link = li.select_one('a[href*="/evento/"]')
            if not link:
                continue
            href = link.get("href", "").strip()
            if not href or any(slug in href.lower() for slug in non_comedy_slugs):
                continue
            ticket_url = urljoin("https://www.ticketline.pt", href)
            if ticket_url in seen_urls:
                continue

            start_el = li.select_one('[itemprop="startDate"]')
            start_raw = (start_el.get("content") or start_el.get("data-date") or "").strip() if start_el else ""
            if start_raw and re.fullmatch(r"\d{4}-\d{2}-\d{2}", start_raw):
                start_raw = f"{start_raw}T21:30:00+00:00"
            dt = parse_iso_datetime(start_raw)
            if not dt:
                continue

            img_el = li.select_one("img")
            image_url = ""
            title = ""
            if img_el:
                src = img_el.get("data-src-original") or img_el.get("data-src") or img_el.get("src") or ""
                if src and "blank.png" not in src:
                    image_url = urljoin("https://www.ticketline.pt", src)
                title = (img_el.get("alt") or "").strip()

            if not title:
                title_el = li.select_one(".details h2, .details h3, .details .title, [itemprop='name']")
                title = title_el.get_text(" ", strip=True) if title_el else ""
            if not title:
                continue

            venue_el = li.select_one('[itemprop="location"], .venues')
            venue = venue_el.get_text(" ", strip=True) if venue_el else "Portugal"
            district = infer_district(f"{venue} {title}", venue_hint=venue)

            seen_urls.add(ticket_url)
            events.append({
                "id": make_id("ticketline", ticket_url, dt.isoformat()),
                "title": title,
                "dateTime": dt.isoformat(),
                "venue": venue,
                "district": district,
                "imageUrl": image_url,
                "ticketUrl": ticket_url,
                "source": "Ticketline",
                "featured": False,
            })
    return events


def fetch_agenda_cultural_porto() -> list[dict[str, Any]]:
    url = "https://agendaculturalporto.org/stand-up-comedy-no-porto-espectaculos/"
    resp = requests.get(url, headers=HEADERS, timeout=20)
    print(f"[AgendaCulturalPorto] {url} -> HTTP {resp.status_code}")
    if resp.status_code != 200:
        return []
    soup = BeautifulSoup(resp.text, "html.parser")
    events = extract_json_ld_events(soup, "Agenda Cultural Porto", "https://agendaculturalporto.org")
    for ev in events:
        if ev.get("district") == "Portugal":
            ev["district"] = "Porto"
    return events


def fetch_teatro_sa_da_bandeira() -> list[dict[str, Any]]:
    url = "https://teatrosadabandeira.pt/eventos/categoria/entretenimento/"
    resp = requests.get(url, headers=HEADERS, timeout=20)
    print(f"[TeatroSaDaBandeira] {url} -> HTTP {resp.status_code}")
    if resp.status_code != 200:
        return []
    soup = BeautifulSoup(resp.text, "html.parser")
    events = extract_json_ld_events(soup, "Teatro Sá da Bandeira", "https://teatrosadabandeira.pt")
    for ev in events:
        if ev.get("district") == "Portugal":
            ev["district"] = "Porto"
        if ev.get("venue") == "Local a anunciar":
            ev["venue"] = "Teatro Sá da Bandeira"
    return events


def fetch_bol_standup() -> list[dict[str, Any]]:
    urls = [
        "https://www.bol.pt/Comprar/Pesquisa?q=stand+up",
        "https://www.bol.pt/Comprar/Pesquisa?q=lisboa+comedy+club",
    ]
    events: list[dict[str, Any]] = []
    for url in urls:
        resp = requests.get(url, headers=HEADERS, timeout=20)
        print(f"[BOL] {url} -> HTTP {resp.status_code}")
        if resp.status_code != 200:
            continue
        soup = BeautifulSoup(resp.text, "html.parser")
        events.extend(extract_json_ld_events(soup, "BOL", "https://www.bol.pt"))

        for card in soup.select("a[href*='/Comprar/Bilhetes/']"):
            href = card.get("href", "")
            if not href:
                continue
            ticket_url = urljoin("https://www.bol.pt", href)
            title = card.get_text(" ", strip=True)
            if not title or len(title) < 3:
                continue
            img_el = card.select_one("img")
            image_url = ""
            if img_el:
                src = img_el.get("data-src") or img_el.get("src") or ""
                image_url = urljoin("https://www.bol.pt", src) if src else ""

            parent_text = card.parent.get_text(" ", strip=True) if card.parent else title
            date_match = re.search(r"(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}(?:\s+\d{1,2}:\d{2})?)", parent_text)
            dt = parse_iso_datetime(date_match.group(1)) if date_match else None
            if not dt:
                continue

            events.append({
                "id": make_id("bol", ticket_url, dt.isoformat()),
                "title": title,
                "dateTime": dt.isoformat(),
                "venue": "Portugal",
                "district": infer_district(f"{title} {parent_text}", venue_hint=parent_text),
                "imageUrl": image_url,
                "ticketUrl": ticket_url,
                "source": "BOL",
                "featured": False,
            })
    return events


def load_existing_events(output_path: Path) -> list[dict[str, Any]]:
    if not output_path.exists():
        return []
    try:
        data = json.loads(output_path.read_text(encoding="utf-8"))
        return data.get("events", [])
    except Exception:
        return []


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    output_path = repo_root / "docs" / "events.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    collected: list[dict[str, Any]] = []

    for name, fetcher in [
        ("Eventbrite", fetch_eventbrite_standup),
        ("Meetup", fetch_meetup_standup),
        ("Ticketline", fetch_ticketline_standup),
        ("BOL", fetch_bol_standup),
        ("TeatroSaDaBandeira", fetch_teatro_sa_da_bandeira),
        ("AgendaCulturalPorto", fetch_agenda_cultural_porto),
    ]:
        try:
            items = fetcher()
            print(f"[{name}] fetched {len(items)} events")
            collected.extend(items)
        except Exception as exc:
            print(f"[{name}] skipped due to error: {exc}")

    sheet_url = os.getenv("GOOGLE_SHEET_CSV_URL", "").strip()
    hidden_urls: set[str] = set()
    if sheet_url:
        try:
            sheet_items, hidden_urls = fetch_google_sheet_csv(sheet_url)
            print(f"[GoogleSheet] fetched {len(sheet_items)} events, {len(hidden_urls)} hidden")
            collected.extend(sheet_items)
        except Exception as exc:
            print(f"[GoogleSheet] error: {exc}")

    sources_csv_url = os.getenv("GOOGLE_SHEET_SOURCES_CSV_URL", "").strip()
    if sources_csv_url:
        try:
            custom_items = fetch_custom_json_ld_sources(sources_csv_url)
            print(f"[CustomSources] fetched {len(custom_items)} events")
            collected.extend(custom_items)
        except Exception as exc:
            print(f"[CustomSources] error: {exc}")

    existing = load_existing_events(output_path)
    merged_by_url: dict[str, dict[str, Any]] = {}
    for ev in existing + collected:
        url_key = (ev.get("ticketUrl") or "").strip()
        if url_key and url_key not in hidden_urls:
            merged_by_url[url_key] = ev

    cutoff = datetime.now(LISBON_TZ) - timedelta(hours=2)
    upcoming: list[tuple[datetime, dict[str, Any]]] = []
    for ev in merged_by_url.values():
        if not is_relevant_portugal_comedy(
            ev.get("title", ""), ev.get("venue", ""), ev.get("source", "")
        ):
            continue
        img = (ev.get("imageUrl") or "").strip()
        if img.startswith("/images/fallbacks/"):
            ev["imageUrl"] = f"https://www.meetup.com{img}"
        dt = parse_iso_datetime(ev.get("dateTime", ""))
        if dt and dt >= cutoff:
            upcoming.append((dt, ev))

    upcoming.sort(key=lambda item: (item[0], not bool(item[1].get("featured", False))))

    payload = {
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "events": [ev for _, ev in upcoming],
    }
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(upcoming)} upcoming events to {output_path}")

    update_online_specials(repo_root / "docs" / "online_specials.json")


def update_online_specials(online_output_path: Path) -> None:
    online_csv_url = os.getenv("GOOGLE_SHEET_ONLINE_CSV_URL", "").strip()
    if not online_csv_url:
        return
    try:
        resp = requests.get(online_csv_url, headers=HEADERS, timeout=20)
        resp.raise_for_status()
    except Exception as exc:
        print(f"[OnlineSheet] error: {exc}")
        return

    existing_by_url: dict[str, dict[str, Any]] = {}
    if online_output_path.exists():
        try:
            existing_data = json.loads(online_output_path.read_text(encoding="utf-8"))
            for sp in existing_data.get("specials", []):
                u = (sp.get("watchUrl") or "").strip()
                if u:
                    existing_by_url[u] = sp
        except Exception:
            pass

    hidden_urls: set[str] = set()
    reader = csv.DictReader(io.StringIO(resp.text))
    for row in reader:
        watch_url = (row.get("watchUrl") or "").strip()
        hide_raw = (row.get("hide") or row.get("ocultar") or "").strip().lower()
        if watch_url and hide_raw in {"true", "1", "sim", "yes"}:
            hidden_urls.add(watch_url)
            continue
        title = (row.get("title") or "").strip()
        platform = (row.get("platform") or "").strip() or "YouTube"
        if platform.lower() == "max":
            platform = "HBO"
        lang = (row.get("language") or "PT").strip().upper()
        if not title or not watch_url or not any(tag in lang for tag in ("PT", "BR")):
            continue
        featured_raw = (row.get("featured") or "").strip().lower()
        published_date = (row.get("publishedDate") or "").strip()
        year = (row.get("year") or "").strip()
        if not year and len(published_date) >= 4:
            year = published_date[:4]
        try:
            priority = int((row.get("priority") or "0").strip() or "0")
        except ValueError:
            priority = 0
        existing_by_url[watch_url] = {
            "id": make_id(platform, watch_url, title),
            "title": title,
            "comedian": (row.get("comedian") or "").strip(),
            "platform": platform,
            "language": lang,
            "publishedDate": published_date,
            "year": year,
            "imageUrl": (row.get("imageUrl") or "").strip(),
            "watchUrl": watch_url,
            "featured": featured_raw in {"true", "1", "sim", "yes"},
            "priority": priority,
        }

    final_specials = [
        sp for url_key, sp in existing_by_url.items() if url_key not in hidden_urls
    ]
    final_specials.sort(
        key=lambda sp: (
            not bool(sp.get("featured", False)),
            "" if sp.get("publishedDate") else "z",
            sp.get("publishedDate", ""),
            int(sp.get("priority", 0)),
        ),
        reverse=False,
    )
    # Sort properly: featured=True first, then publishedDate descending, then priority descending
    final_specials.sort(
        key=lambda sp: (
            1 if sp.get("featured", False) else 0,
            sp.get("publishedDate") or (f"{sp.get('year', '')}-01-01" if sp.get("year") else ""),
            int(sp.get("priority", 0)),
        ),
        reverse=True,
    )
    online_payload = {
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "specials": final_specials,
    }
    online_output_path.write_text(
        json.dumps(online_payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Wrote {len(final_specials)} online specials to {online_output_path}")


if __name__ == "__main__":
    main()
