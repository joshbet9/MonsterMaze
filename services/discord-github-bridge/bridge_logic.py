"""Pure formatting and state helpers for the Discord bridge."""
import re


REPOSITORY_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


def repository_allowlist(value):
    repositories = set()
    for item in value.split(","):
        repository = item.strip()
        if repository and REPOSITORY_RE.fullmatch(repository):
            repositories.add(repository.casefold())
    return frozenset(repositories)


def state_change(previous_state, previous_starter, new_state, new_starter):
    """Do not announce initial state; announce transitions and changed starters."""
    if previous_state is None:
        return False
    if previous_state != new_state:
        return True
    return new_state == "starting" and bool(new_starter) and previous_starter != new_starter


def format_activity(server, state, started_by=None):
    label = "Testplex 26.3" if server in {"1.21", "26.3", "testplex-modern"} else server
    if state == "starting":
        suffix = f" — started by {started_by}" if started_by else ""
        return f"🟡 **{label} is starting**{suffix}."
    if state == "online":
        return f"🟢 **{label} is online.**"
    if state == "offline":
        return f"🔴 **{label} is offline.**"
    return f"⚪ **{label} status:** {state}."


def format_release(repository, release):
    title = (release.get("name") or release.get("tag_name") or "New release").strip()
    tag = release.get("tag_name", "")
    url = release.get("html_url", "")
    body = (release.get("body") or "").strip()
    if len(body) > 3000:
        body = body[:2997].rstrip() + "..."
    lines = [f"📦 **{repository} — {title}**"]
    if tag:
        lines.append(f"Release: `{tag}`")
    if body:
        lines.append(body)
    if url:
        lines.append(url)
    author = release.get("author", {}).get("login")
    if author:
        lines.append(f"Published by {author}")
    return "\n\n".join(lines)


def format_subissue(issue):
    status = "🟢 Open" if issue.get("state") == "open" else "✅ Closed"
    labels = ", ".join(label.get("name", "") for label in issue.get("labels", []) if label.get("name"))
    suffix = f" · `{labels}`" if labels else ""
    return f"{status} · [#{issue['number']} {issue['title']}]({issue['html_url']}){suffix}"
