# How we work in parallel

`main` must always run, because it is what we demo. Everyone works on their own branch, and changes reach `main` only through a pull request (PR).

## First time

```bash
git clone https://github.com/ruturajnalbalwar-arch/Sthira.git
cd Sthira
```

Accept the collaborator invite first: check your email or https://github.com/notifications.

## Every piece of work

```bash
# 1. Start from the latest main
git switch main
git pull

# 2. Make a branch named <you>/<what>
git switch -c arnav/chair-stand-counter

# 3. Commit small and often
git add <files>
git commit -m "Count a rep only when the head reaches the standing band"

# 4. Push and open a PR
git push -u origin arnav/chair-stand-counter
gh pr create --fill          # or open the PR on github.com
```

Ask a teammate to review in the team chat. When one person approves, merge with **Squash and merge**. GitHub deletes the branch after the merge.

## Keeping your branch up to date

If `main` moved while you were working, bring it into your branch before opening or merging the PR:

```bash
git switch main && git pull
git switch arnav/chair-stand-counter
git merge main               # fix any conflicts, then commit
git push
```

## Avoiding conflicts

Each person mostly edits their own area, so parallel work merges cleanly:

| Area | Owner |
|---|---|
| Pose pipeline (camera, landmarks, rep rules, sway, timers) | Arnav |
| Screens, components, styling | Prithvi |
| Backend, database, scoring against norms, AI report | Ruturaj |

If you need to change someone else's area, say so in the chat first, and keep that change in its own small PR.

## Rules

- Never commit secrets. API keys go in `.env`, which git ignores; share them privately.
- Never commit videos of real people. Test clips stay on your own machine.
- One PR does one thing. Small PRs merge without fights.
