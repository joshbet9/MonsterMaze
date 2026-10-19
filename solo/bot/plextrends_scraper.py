"""HTML parser for PlexTrends' public live-player dashboard.

No Mineplex API key is needed. The parser deliberately scopes counts to the
"Currently Active Players" section and ignores historical charts elsewhere on
the page so historical game mentions cannot trigger a live alert.
"""
import re
from html.parser import HTMLParser


_GAME_PATTERNS = (
    ("Mixed Arcade", (r"mixed[\s_-]*arcade",)),
    ("Super Smash Mobs Legacy", (r"super[\s_-]*smash[\s_-]*mobs[\s_-]*legacy",)),
    ("Nano Games", (r"nano[\s_-]*games?",)),
    ("Micro Battles", (r"micro[\s_-]*battles?",)),
    ("Castle Siege", (r"castle[\s_-]*siege",)),
    ("Cake Wars", (r"cake[\s_-]*wars",)),
    ("Speed Builders", (r"speed[\s_-]*builders?",)),
    ("Survival Games", (r"survival[\s_-]*games?",)),
    ("Super Paintball", (r"super[\s_-]*paintball",)),
    ("Dragon Escape", (r"dragon[\s_-]*escape",)),
    ("Block Hunt", (r"block[\s_-]*hunt",)),
    ("Minestrike", (r"mine[\s_-]*strike",)),
    ("Champions", (r"champions?",)),
    ("Clans", (r"clans?",)),
    ("Bridges", (r"the[\s_-]*bridges|bridges",)),
    ("Turf Wars", (r"turf[\s_-]*wars",)),
    ("SkyWars", (r"sky[\s_-]*wars?",)),
    ("One Block", (r"one[\s_-]*block",)),
    ("One in the Quiver", (r"one[\s_-]*in[\s_-]*the[\s_-]*quiver|oitq",)),
    ("Lobby", (r"lobbies|lobby",)),
    ("MPS Legacy", (r"mps[\s_-]*legacy",)),
)

_BLOCK_TAGS = {
    "address", "article", "aside", "blockquote", "br", "dd", "div", "dl",
    "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2",
    "h3", "h4", "h5", "header", "hr", "li", "main", "nav", "ol", "p",
    "section", "table", "tbody", "td", "th", "thead", "tr", "ul",
}
_IGNORED_TAGS = {"script", "style", "noscript", "svg", "head"}


class _VisibleText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.ignored_depth = 0

    def handle_starttag(self, tag, attrs):
        tag = tag.lower()
        if tag in _IGNORED_TAGS:
            self.ignored_depth += 1
        if not self.ignored_depth and tag in _BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        tag = tag.lower()
        if tag in _IGNORED_TAGS and self.ignored_depth:
            self.ignored_depth -= 1
        if not self.ignored_depth and tag in _BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data):
        if not self.ignored_depth and data.strip():
            self.parts.append(" " + data.strip() + " ")


def visible_text(html):
    parser = _VisibleText()
    parser.feed(html)
    parser.close()
    return re.sub(r"\s+", " ", " ".join(parser.parts)).strip()


def _count_after(label_pattern, section):
    match = re.search(
        r"(?:^|\b)(?:" + label_pattern + r")"
        + r"\b\s*(?:players?\s*)?(?:[:=–—-]\s*)?(\d[\d,]*)\b",
        section,
        re.IGNORECASE,
    )
    if match:
        return int(match.group(1).replace(",", ""))
    # Some layouts place the count before the game label.
    match = re.search(
        r"\b(\d[\d,]*)\s*(?:players?\s+)?"
        + r"(?:" + label_pattern + r")" + r"(?:\b|$)",
        section,
        re.IGNORECASE,
    )
    return int(match.group(1).replace(",", "")) if match else None


def parse_live_counts(html):
    """Return the current visible per-game counts or raise if the section is missing."""
    text = visible_text(html)
    active = re.search(
        r"Currently\s+Active\s+Players(.*?)(?:Total\s+Unique\s+Players|"
        r"Historical\s+Player\s+Trends|Total\s+Player\s+Volume\s+Range)",
        text,
        re.IGNORECASE,
    )
    if not active:
        raise ValueError("PlexTrends page did not expose the Currently Active Players section")
    section = active.group(1)
    total_match = re.search(
        r"(?:Online|Players\s+Online|Network\s+Online)\s*:?\s*(\d[\d,]*)",
        section,
        re.IGNORECASE,
    )
    if not total_match:
        raise ValueError("PlexTrends live-player section did not expose the network online count")
    network_players = int(total_match.group(1).replace(",", ""))

    rows = []
    found_mixed_arcade = False
    for name, patterns in _GAME_PATTERNS:
        count = None
        for pattern in patterns:
            count = _count_after(pattern, section)
            if count is not None:
                break
        if name == "Mixed Arcade":
            found_mixed_arcade = count is not None
        if count is not None:
            rows.append({"name": name, "count": max(0, count)})

    # The active-player section is the list of current non-empty game rows; an
    # omitted game is inactive only after the section and total were parsed.
    mixed = next((row["count"] for row in rows if row["name"] == "Mixed Arcade"), 0)
    rows.sort(key=lambda row: (-row["count"], row["name"].casefold()))
    if not rows and network_players > 0:
        raise ValueError("PlexTrends live-player section had a total but no parseable game rows")
    return {
        "network_players": network_players,
        "game_counts": rows,
        "mixed_arcade_count": mixed,
        "mixed_arcade_explicit": found_mixed_arcade,
        "source": "https://plextrends.com/",
    }
