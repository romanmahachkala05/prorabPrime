# Roadmap

What is planned and not started. A plan here is an intention, not a decision: when one is taken up,
the choices that constrain later work go to [`DECISIONS.md`](DECISIONS.md) first.

## Several users on one server

**Why.** Today the server has one database and one API token, so everybody who has the token sees and
changes the same objects (ADR-0005, the "Auth" rule in [`ARCHITECTURE.md`](ARCHITECTURE.md) §11). A friend
who gets the app would see the owner's customers, and the owner would see his. Wanted: each person has
their own account and their own data, on one server.

**What it means.** Not privacy from the person who runs the server: the database and the files are on his
computer, and he can read them there. It separates people inside the app. A person who wants nobody
to see his data runs his own server.

**Plan** (in this order, each its own branch):

1. **The decision (ADR).** Accounts and tokens: a `users` table, a token per user stored only as a hash,
   a token mapped to its user on every request. Who makes accounts: the owner, from a command on the server
   (no sign-up page, no passwords in the app). One person's tokens never see another's data. Decide the
   fate of the single `API_TOKEN` in `.env` (it becomes the owner's first token).
2. **Schema (Flyway migration).** `users`; `owner_id` on `objects` and on `tasks` (everything else hangs on
   an object and is reached through it). The existing data goes to the first user, so nothing is lost.
3. **Server.** Auth resolves a token to a user and puts it in the call. Repositories take the owner and
   filter by it, so a route cannot forget to: lists, `find`, the trash, tasks, expenses, file serving
   (`/files/{objectId}/...` checks the object's owner), geocoding stays shared. Services and routes change
   only where they pass the owner along. A client-chosen id that belongs to another user is a conflict,
   as it already is for another owner's id (ADR-0017).
4. **Account commands.** Make a user and print his token once; list users; revoke a token. A command of the
   server's own, not an HTTP endpoint.
5. **Tests.** The heart of it: for every route, a second user gets 404 for the first one's records and sees
   none of them in lists, search, the trash, receipts and files. Run against PostgreSQL, not only fakes.
6. **App.** Almost nothing: it already sends one token. It may show whose account it is and say clearly when
   the token is refused (it already does).
7. **Docs.** README: making accounts; what the owner of the server can and cannot see, said plainly.

**Not in scope:** sharing an object between users, roles, a web sign-in, passwords.

**Open questions.** Does the owner need to see all users' data (support, backup), or is each strictly
separate? How is a lost token replaced (the owner makes a new one)? Does each user get a quota for files?
