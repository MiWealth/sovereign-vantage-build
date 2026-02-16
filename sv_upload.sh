#!/bin/sh
# ============================================================================
# SOVEREIGN VANTAGE — Git Upload Script for Termux
#
# Handles EVERYTHING: installs tools, configures git, extracts your zip,
# creates the directory structure, commits, and pushes to GitHub.
#
# Usage:
#   cp this file to ~ then:
#   sh upload.sh
#
# © 2025-2026 MiWealth Pty Ltd. All Rights Reserved.
# ============================================================================

set -e

echo ""
echo "============================================================"
echo "  SOVEREIGN VANTAGE — Git Upload"
echo "  MiWealth Pty Ltd (Australia)"
echo "============================================================"
echo ""

# ── 1. Install tools if missing ─────────────────────────────────────────────
echo "[1/8] Checking tools..."

need_install=""
command -v git > /dev/null 2>&1 || need_install="git $need_install"
command -v unzip > /dev/null 2>&1 || need_install="unzip $need_install"
command -v ssh-keygen > /dev/null 2>&1 || need_install="openssh $need_install"

if [ -n "$need_install" ]; then
    echo "  Installing: $need_install"
    pkg update -y > /dev/null 2>&1
    pkg install -y $need_install
else
    echo "  All tools present."
fi

# ── 2. Configure git identity ───────────────────────────────────────────────
echo "[2/8] Git identity..."

if [ -z "$(git config --global user.name)" ]; then
    printf "  Your name [MiWealth]: "
    read GIT_NAME
    GIT_NAME="${GIT_NAME:MiWealth}"
    git config --global user.name "$GIT_NAME"
fi
echo "  Name:  $(git config --global user.name)"

if [ -z "$(git config --global user.email)" ]; then
    printf "  Your GitHub email: "
    read GIT_EMAIL
GIT_EMAIL="${GIT_EMAIL:stahl.m@pm.me}"
    git config --global user.email "$GIT_EMAIL"
fi
echo "  Email: $(git config --global user.email)"

git config --global init.defaultBranch main
git config --global pull.rebase false

# ── 3. SSH key ──────────────────────────────────────────────────────────────
echo "[3/8] SSH key..."

if [ ! -f "$HOME/.ssh/id_ed25519" ]; then
    echo "  Generating SSH key..."
    mkdir -p "$HOME/.ssh"
    ssh-keygen -t ed25519 -C "$(git config --global user.email)" -f "$HOME/.ssh/id_ed25519" -N ""
    echo ""
    echo "  =========================================================="
    echo "  ADD THIS KEY TO GITHUB:"
    echo "  github.com -> Settings -> SSH and GPG keys -> New SSH key"
    echo "  =========================================================="
    echo ""
    cat "$HOME/.ssh/id_ed25519.pub"
    echo ""
    printf "  Press ENTER after adding the key to GitHub... "
    read DUMMY
else
    echo "  SSH key exists."
fi

# ── 4. Find the zip ────────────────────────────────────────────────────────
echo "[4/8] Finding your zip..."

CLAUDE_DIR="$HOME/storage/downloads/000 Claude/Files"
DOWNLOADS="$HOME/storage/downloads"

# Look for the most recent sovereign-vantage zip
ZIP_FILE=""

# Check Claude folder first
if [ -d "$CLAUDE_DIR" ]; then
    ZIP_FILE=$(ls -t "$CLAUDE_DIR"/sovereign-vantage-v*.zip 2>/dev/null | head -1)
fi

# Fall back to Downloads root
if [ -z "$ZIP_FILE" ] && [ -d "$DOWNLOADS" ]; then
    ZIP_FILE=$(ls -t "$DOWNLOADS"/sovereign-vantage-v*.zip 2>/dev/null | head -1)
fi

# Fall back to home directory
if [ -z "$ZIP_FILE" ]; then
    ZIP_FILE=$(ls -t "$HOME"/sovereign-vantage-v*.zip 2>/dev/null | head -1)
fi

if [ -z "$ZIP_FILE" ]; then
    echo ""
    echo "  No sovereign-vantage zip found automatically."
    echo ""
    echo "  Looked in:"
    echo "    $CLAUDE_DIR"
    echo "    $DOWNLOADS"
    echo "    $HOME"
    echo ""
    printf "  Type the full path to the zip: "
    read ZIP_FILE
    if [ ! -f "$ZIP_FILE" ]; then
        echo "  File not found. Exiting."
        exit 1
    fi
fi

echo "  Found: $ZIP_FILE"

# ── 5. Set up repo directory ───────────────────────────────────────────────
echo "[5/8] Setting up repo..."

REPO="$HOME/sovereign-vantage-android"

mkdir -p "$REPO"
cd "$REPO"

if [ ! -d ".git" ]; then
    git init
    git remote add origin git@github.com:MiWealth/sovereign-vantage-android.git
    echo "  Initialised new repo."
else
    echo "  Repo already exists — updating."
fi

# ── 6. Extract zip ─────────────────────────────────────────────────────────
echo "[6/8] Extracting..."

# Extract to a temp directory first
TMPDIR="$HOME/.sv_extract_tmp"
rm -rf "$TMPDIR"
mkdir -p "$TMPDIR"

unzip -o "$ZIP_FILE" -d "$TMPDIR" > /dev/null 2>&1

# The zip has files under android/ — strip that prefix
if [ -d "$TMPDIR/android" ]; then
    echo "  Stripping android/ prefix..."
    cp -a "$TMPDIR/android/." "$REPO/"
    echo "  Done."
else
    echo "  Copying contents into repo..."
    cp -a "$TMPDIR/." "$REPO/"
    echo "  Done."
fi

# Clean up non-repo files that may be in the zip root
rm -f "$REPO/GITHUB_GUIDE_FOR_SOVEREIGN_VANTAGE.md" 2>/dev/null
rm -f "$REPO/SESSION_HANDOFF_FEB08_v5_5_87_EXCHANGE_TESTING.md" 2>/dev/null

# Clean up temp
rm -rf "$TMPDIR"

# Make gradlew executable
chmod +x "$REPO/gradlew" 2>/dev/null

# ── 7. Show what we've got ─────────────────────────────────────────────────
echo "[7/8] Repository contents:"
echo ""

KT_COUNT=$(find "$REPO" -name "*.kt" | wc -l)
DIR_COUNT=$(find "$REPO" -type d | grep -v ".git" | wc -l)
TOTAL_FILES=$(find "$REPO" -type f | grep -v ".git/" | wc -l)

echo "  Kotlin files:  $KT_COUNT"
echo "  Directories:   $DIR_COUNT"
echo "  Total files:   $TOTAL_FILES"
echo ""

echo "  Root files:"
for f in README.md LICENSE SECURITY.md CHANGELOG.md .gitignore; do
    if [ -f "$REPO/$f" ]; then
        echo "    [OK] $f"
    else
        echo "    [!!] $f — MISSING"
    fi
done

echo ""
printf "  Push to GitHub? (y/n): "
read CONFIRM

if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo ""
    echo "  Aborted. Repo is at: $REPO"
    echo "  To push manually:"
    echo "    cd $REPO"
    echo "    git add -A && git commit -m \"message\" && git push -u origin main"
    exit 0
fi

# ── 8. Commit and push ─────────────────────────────────────────────────────
echo "[8/8] Pushing..."

cd "$REPO"
git add -A

# Detect version from build.gradle.kts
VERSION=$(grep 'versionName' app/build.gradle.kts 2>/dev/null | sed 's/.*"\(.*\)".*/\1/' | head -1)
VERSION="${VERSION:-unknown}"

# Initial or update?
if git rev-parse HEAD > /dev/null 2>&1; then
    COMMIT_MSG="Update: Sovereign Vantage $VERSION"
else
    COMMIT_MSG="Initial commit: Sovereign Vantage $VERSION Arthur Edition"
fi

echo "  Commit: $COMMIT_MSG"

git commit -m "$COMMIT_MSG"
git push -u origin main

echo ""
echo "============================================================"
echo "  DONE"
echo ""
echo "  github.com/MiWealth/sovereign-vantage-android"
echo "  Version: $VERSION"
echo "  Files:   $TOTAL_FILES ($KT_COUNT Kotlin)"
echo "============================================================"
echo ""
echo "  For Arthur. For Cathryn. For generational wealth."
echo ""
