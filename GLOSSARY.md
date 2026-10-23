# Comcord

A Matrix chat client with a Discord-style layout, so communities moving from Discord to Matrix feel at home. The words below are the ones people see in the app. Matrix protocol terms are listed only where they differ.

## Language

### Community structure

**Server**:
A Matrix space the user has joined that no other joined space lists as a child. Each Server gets an icon on the Rail. Joined spaces that do have a joined parent show up as Categories, not as Servers.
_Avoid_: Guild, space (in UI copy), community

**Category**:
A collapsible group of Channels inside a Server. It stands for one subspace directly under that Server; Channels from deeper subspaces are folded into that same Category.
_Avoid_: Subspace (in UI copy), section, folder

**Channel**:
A Matrix room that isn't a space and isn't a DM. A Channel belongs to a Server when that space (or one of its subspaces) lists it as a child; whether the room itself names a parent doesn't matter. A Channel can belong to several Servers at once (and appears in each of them, with shared unread state), or to none.
_Avoid_: Room (in UI copy)

**Joined Channel**:
A Channel the user is a member of.

**Joinable Channel**:
A Channel listed in a Server that the user hasn't joined yet. It looks different from both Joined and Muted Channels, and opening it shows a Channel Preview instead of joining.
_Avoid_: Unjoined room, available room

**Muted Channel**:
A Joined Channel whose notifications the user has turned off. It's a separate idea from Joinable Channel and must never look the same.

**Channel Preview**:
What you see when you open a Joinable Channel without joining it: recent history if the room allows outsiders to read it, otherwise a summary card (name, topic, avatar, member count, join rule). It always has a Join action; if the user isn't allowed to join, the action is disabled and explains why.

**Homeserver**:
The Matrix server that hosts a user's account (for example `matrix.org`). Always say "homeserver", never just "server", so it can't be confused with a Server.
_Avoid_: Server (when meaning the homeserver), instance

**Direct Message (DM)**:
A room listed in the Account's `m.direct` data, with one or more other people. It never belongs to a Server. A group chat that isn't listed in `m.direct` is not a DM; if no joined Server contains it, it's an Orphan Room.
_Avoid_: Private chat, PM

**Invite**:
A pending invitation for the Active Account to join a Channel, DM, or Server. All Invites are gathered in one central place rather than scattered across Servers; where that place lives is still open.
_Avoid_: Message request, knock

**Account**:
One Matrix user ID signed in on a Homeserver. Several Accounts can be signed in at once, but only one is used at a time.
_Avoid_: Profile, login, session (in UI copy)

**Active Account**:
The Account whose Servers, Views, DMs, selection and drafts currently fill the whole client. Switching Accounts replaces everything on screen; Accounts are never merged into one Rail.

### Navigation

**Rail**:
The narrow vertical strip on the far left that lists Servers and other top-level destinations.
_Avoid_: Sidebar, server list, guild bar

**Channel List**:
The panel next to the Rail that shows the selected Server's or View's Channels, Categories, or DMs.
_Avoid_: Room list, sidebar

**Member List**:
The panel on the right that shows a Channel's members, grouped by Role.
_Avoid_: User list, people pane

**Role**:
The user-facing name for a Matrix power level band, such as Admin (100), Moderator (50), Member (0), or a custom level. Each person has exactly one Role per Channel, read from that Channel's own power levels, never the Server's. Roles have no colours and can't be stacked.
_Avoid_: Power level (in UI copy), rank, permission group

**User Panel**:
The strip at the bottom-left of the Channel List showing the Active Account. It holds the account switcher, the user's Presence, and the way into settings.
_Avoid_: Profile bar, footer

**Presence**:
The user's online status (online, idle/unavailable, offline) plus an optional status message, as defined by Matrix presence. Some homeservers turn presence off, so the UI must cope with it being missing.
_Avoid_: Status (on its own), activity

**Unread Indicator**:
A dot on a Channel, Server, or View meaning a Joined Channel inside has messages the user hasn't read. Muted Channels never turn it on.

**Mention Badge**:
A number on a Channel, Server, or View counting messages that mention or highlight the user, added up across every Joined Channel inside, Muted Channels included.

**View**:
A built-in Rail entry that shows a filtered set of the user's rooms rather than a real Matrix space. A View has no members, settings, or invites, and each View can be hidden by the user.
_Avoid_: Pseudo-server, virtual server, smart server

**All Rooms View**:
The View listing every Joined Channel, whether or not it belongs to a Server. It never includes DMs.

**Direct Messages View**:
The View listing every DM. It plays the part of Discord's Home button.
_Avoid_: Home

**Orphan Rooms View**:
The View listing Channels that don't belong to any Server the user has joined.
_Avoid_: Unsorted, Other rooms
