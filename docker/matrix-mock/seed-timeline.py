#!/usr/bin/env python3
"""Seed unencrypted reading fixtures on the local development homeserver."""

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from contextlib import contextmanager


BASE_URL = os.environ.get("SYNAPSE_URL", "http://synapse:8008").rstrip("/")
SEED_KEY = "dev.phillipslabs.comcord.seed"


def segment(value):
    return urllib.parse.quote(value, safe="")


def request(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(6):
        req = urllib.request.Request(
            BASE_URL + "/_matrix/client/v3" + path,
            data=json.dumps(data).encode() if data is not None else None,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 5:
                raise
            details = json.load(error)
            time.sleep(max(1, details.get("retry_after_ms", 1000) / 1000))


def ensure_channel(token, local_alias, name):
    alias = f"#{local_alias}:localhost"
    try:
        room_id = request("GET", "/directory/room/" + segment(alias), token=token)["room_id"]
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        room_id = request(
            "POST",
            "/createRoom",
            {
                "room_alias_name": local_alias,
                "name": name,
                "topic": "Local Comcord timeline fixture (unencrypted).",
                "preset": "private_chat",
            },
            token,
        )["room_id"]
    request("POST", "/join/" + segment(room_id), {}, token)
    return room_id


def message_history(token, room_id):
    messages = []
    cursor = None
    while True:
        query = {"dir": "b", "limit": 100}
        if cursor:
            query["from"] = cursor
        page = request(
            "GET",
            f"/rooms/{segment(room_id)}/messages?" + urllib.parse.urlencode(query),
            token=token,
        )
        for event in page["chunk"]:
            if event["type"] == "m.room.message":
                messages.append(event)
        next_cursor = page.get("end")
        if not page["chunk"] or not next_cursor or next_cursor == cursor:
            break
        cursor = next_cursor
    return messages


def seed_messages(alice_token, bob_token, room_id, fixture, count):
    # Inspect history because transaction IDs are scoped to an access token.
    existing = {
        event["content"].get(SEED_KEY)
        for event in message_history(alice_token, room_id)
        if isinstance(event["content"].get(SEED_KEY), str)
    }

    added = 0
    examples = [
        "Hey Bob, can you help check the timeline today?",
        "Sure, Alice! I'll start by scrolling through the older messages.",
        "Can you see these characters? café, 日本語, 👋",
        "Yes, they all look good here. 👋",
        "Here's our checklist:\nCheck the oldest message\nSwitch Channels\nReturn to the conversation",
        "Got it. I'll check that the messages stay in the right order too.",
        "I'm checking the narrow layout now. This longer message should wrap across several lines "
        "without hiding its sender or pushing the conversation off the screen. "
        "Could you check how it looks on your display as well?",
        "Looks readable on my side. The long message wraps, and I can still follow the conversation.",
        "Thanks! Shall we switch to the other Channel and come back?",
        "Let's do that. We should see this same conversation when we return.",
    ]
    for index in range(1, count + 1):
        marker = f"{fixture}-v2-{index}"
        if marker in existing:
            continue
        body = f"{fixture} {index:03d}/{count:03d} — {examples[(index - 1) % len(examples)]}"
        request(
            "PUT",
            f"/rooms/{segment(room_id)}/send/m.room.message/{segment(marker)}",
            {"msgtype": "m.text", "body": body, SEED_KEY: marker},
            alice_token if index % 2 else bob_token,
        )
        added += 1
        if added % 20 == 0:
            print(f"{fixture}: added {added} messages...", flush=True)
    return added


@contextmanager
def login(username, password):
    login = request(
        "POST",
        "/login",
        {
            "type": "m.login.password",
            "identifier": {"type": "m.id.user", "user": username},
            "password": password,
            "initial_device_display_name": "Comcord local timeline seed",
        },
    )
    token = login["access_token"]
    try:
        yield token
    finally:
        request("POST", "/logout", {}, token)


def membership(token, room_id, user_id):
    try:
        return request(
            "GET", f"/rooms/{segment(room_id)}/state/m.room.member/" + segment(user_id), token=token,
        )["membership"]
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        return None


def join_bob(alice_token, bob_token, room_id):
    path = f"/rooms/{segment(room_id)}"
    state = membership(alice_token, room_id, "@bob:localhost")
    if state not in ("join", "invite"):
        request("POST", path + "/invite", {"user_id": "@bob:localhost"}, alice_token)
    if state != "join":
        request("POST", "/join/" + segment(room_id), {}, bob_token)


def retire_old_fixture(alice_token, bob_token, alias, name, count):
    try:
        room_id = request(
            "GET", "/directory/room/" + segment(f"#{alias}:localhost"), token=alice_token,
        )["room_id"]
        if membership(alice_token, room_id, "@alice:localhost") != "join":
            return
        messages = message_history(alice_token, room_id)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        return
    markers = {f"{name}-v1-{i}" for i in range(1, count + 1)}
    markers.update(f"{name} Bob-v1-{i}" for i in range(1, 5))
    if not messages or any(
        event["content"].get(SEED_KEY) not in markers
        or event["sender"] not in ("@alice:localhost", "@bob:localhost")
        for event in messages
    ):
        print(f"Keeping previous {name}: it may contain user-added messages.", flush=True)
        return
    # Leave rather than delete/redact history. Only retire untouched seed data.
    if membership(alice_token, room_id, "@bob:localhost") in ("join", "invite"):
        request("POST", f"/rooms/{segment(room_id)}/leave", {}, bob_token)
    request("POST", f"/rooms/{segment(room_id)}/leave", {}, alice_token)
    print(f"Left previous {name}; interleaved fixture replaces it.", flush=True)


def main():
    with login("alice", "alicepass") as token, login("bob", "bobpass") as bob_token:
        for alias, name, count in [
            ("comcord-seed-history", "Timeline History", 80),
            ("comcord-seed-switching", "Timeline Switching", 8),
            ("comcord-seed-empty", "Timeline Empty", 0),
        ]:
            current_alias = alias + "-v2" if count else alias
            room_id = ensure_channel(token, current_alias, name)
            if count:
                join_bob(token, bob_token, room_id)
            added = seed_messages(token, bob_token, room_id, name, count)
            print(f"{name}: added {added} alternating messages ({room_id})", flush=True)
            if count:
                retire_old_fixture(token, bob_token, alias, name, count)


if __name__ == "__main__":
    main()
