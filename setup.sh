#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
#  Forge — One-shot setup script
#  Installs all dependencies, sets PATH, and launches Forge UI.
#
#  Usage:
#    chmod +x setup.sh
#    ./setup.sh            # full install + launch Forge UI
#    ./setup.sh --check    # verify everything is installed (no install)
#    ./setup.sh --start    # skip install, just start Forge UI
# ═══════════════════════════════════════════════════════════════════

set -e

FORGE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_ONLY=false
START_ONLY=false

for arg in "$@"; do
  case $arg in
    --check) CHECK_ONLY=true ;;
    --start) START_ONLY=true ;;
  esac
done

# ── Colours ─────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

ok()   { echo -e "  ${GREEN}✅  $*${RESET}"; }
warn() { echo -e "  ${YELLOW}⚠️   $*${RESET}"; }
err()  { echo -e "  ${RED}❌  $*${RESET}"; }
info() { echo -e "  ${CYAN}ℹ   $*${RESET}"; }
step() { echo -e "\n${BOLD}${BLUE}▶ $*${RESET}"; }
sep()  { echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"; }

ERRORS=()
flag_error() { ERRORS+=("$1"); }

sep
echo -e "${BOLD}  ⚡ Forge Setup${RESET}"
sep

# ── Detect shell profile ─────────────────────────────────────────────
detect_profile() {
  if [[ -n "$ZSH_VERSION" ]] || [[ "$SHELL" == */zsh ]]; then
    echo "$HOME/.zshrc"
  elif [[ -n "$BASH_VERSION" ]] || [[ "$SHELL" == */bash ]]; then
    [[ -f "$HOME/.bash_profile" ]] && echo "$HOME/.bash_profile" || echo "$HOME/.bashrc"
  else
    echo "$HOME/.profile"
  fi
}
PROFILE=$(detect_profile)

# ── Append to profile if line not already there ──────────────────────
add_to_profile() {
  local line="$1"
  grep -qF "$line" "$PROFILE" 2>/dev/null || echo "$line" >> "$PROFILE"
}

# ── Source updated profile ───────────────────────────────────────────
reload_profile() {
  # shellcheck disable=SC1090
  source "$PROFILE" 2>/dev/null || true
}

# ═══════════════════════════════════════════════════════════════════
# 1. Homebrew
# ═══════════════════════════════════════════════════════════════════
step "1/8  Homebrew"

if command -v brew &>/dev/null; then
  ok "Homebrew $(brew --version | head -1)"
else
  if $CHECK_ONLY; then
    err "Homebrew not found"; flag_error "Homebrew"
  else
    info "Installing Homebrew…"
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    # Apple Silicon path
    if [[ -f /opt/homebrew/bin/brew ]]; then
      add_to_profile 'eval "$(/opt/homebrew/bin/brew shellenv)"'
      eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
    ok "Homebrew installed"
  fi
fi

# ═══════════════════════════════════════════════════════════════════
# 2. Java 17+
# ═══════════════════════════════════════════════════════════════════
step "2/8  Java 17+"

JAVA_OK=false
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/{print $2}' | cut -d. -f1)
  if [[ "$JAVA_VER" -ge 17 ]]; then
    ok "Java $JAVA_VER ($(java -version 2>&1 | head -1))"
    JAVA_OK=true
  else
    warn "Java $JAVA_VER found but 17+ required"
  fi
fi

if ! $JAVA_OK; then
  if $CHECK_ONLY; then
    err "Java 17+ not found"; flag_error "Java 17+"
  else
    info "Installing OpenJDK 17 via Homebrew…"
    brew install --quiet openjdk@17

    JAVA_HOME_PATH="$(brew --prefix openjdk@17)"
    add_to_profile "export JAVA_HOME=\"${JAVA_HOME_PATH}\""
    add_to_profile "export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    export JAVA_HOME="$JAVA_HOME_PATH"
    export PATH="$JAVA_HOME/bin:$PATH"

    # macOS system link
    sudo ln -sfn "${JAVA_HOME_PATH}/libexec/openjdk.jdk" \
      /Library/Java/JavaVirtualMachines/openjdk-17.jdk 2>/dev/null || true

    ok "Java 17 installed  →  JAVA_HOME=$JAVA_HOME"
  fi
fi

# ═══════════════════════════════════════════════════════════════════
# 3. Maven 3.8+
# ═══════════════════════════════════════════════════════════════════
step "3/8  Maven 3.8+"

if command -v mvn &>/dev/null; then
  MVN_VER=$(mvn -version 2>/dev/null | awk '/Apache Maven/{print $3}')
  ok "Maven $MVN_VER"
else
  if $CHECK_ONLY; then
    err "Maven not found"; flag_error "Maven"
  else
    info "Installing Maven…"
    brew install --quiet maven
    ok "Maven installed  →  $(mvn -version 2>/dev/null | head -1)"
  fi
fi

# ═══════════════════════════════════════════════════════════════════
# 4. Node.js 20+ via nvm
# ═══════════════════════════════════════════════════════════════════
step "4/8  Node.js 20+"

NODE_OK=false
if command -v node &>/dev/null; then
  NODE_VER=$(node --version | tr -d 'v' | cut -d. -f1)
  if [[ "$NODE_VER" -ge 20 ]]; then
    ok "Node.js $(node --version)"
    NODE_OK=true
  else
    warn "Node.js v$NODE_VER found but 20+ required"
  fi
fi

if ! $NODE_OK; then
  if $CHECK_ONLY; then
    err "Node.js 20+ not found"; flag_error "Node.js 20+"
  else
    # Install nvm if missing
    if ! command -v nvm &>/dev/null && [[ ! -f "$HOME/.nvm/nvm.sh" ]]; then
      info "Installing nvm…"
      curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
    fi

    export NVM_DIR="$HOME/.nvm"
    # shellcheck disable=SC1091
    [[ -f "$NVM_DIR/nvm.sh" ]] && source "$NVM_DIR/nvm.sh"

    add_to_profile 'export NVM_DIR="$HOME/.nvm"'
    add_to_profile '[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"'

    info "Installing Node.js 20…"
    nvm install 20
    nvm use 20
    nvm alias default 20

    ok "Node.js $(node -version) via nvm"
  fi
fi

# ═══════════════════════════════════════════════════════════════════
# 5. Android SDK / ADB
# ═══════════════════════════════════════════════════════════════════
step "5/8  Android SDK / ADB"

ADB_OK=false
# Common Android SDK locations
ANDROID_SDK_CANDIDATES=(
  "$HOME/Library/Android/sdk"
  "$HOME/Android/Sdk"
  "/usr/local/share/android-sdk"
  "$ANDROID_HOME"
  "$ANDROID_SDK_ROOT"
)

for candidate in "${ANDROID_SDK_CANDIDATES[@]}"; do
  if [[ -n "$candidate" && -f "$candidate/platform-tools/adb" ]]; then
    export ANDROID_HOME="$candidate"
    export PATH="$ANDROID_HOME/platform-tools:$PATH"
    add_to_profile "export ANDROID_HOME=\"$ANDROID_HOME\""
    add_to_profile "export PATH=\"\$ANDROID_HOME/platform-tools:\$PATH\""
    ADB_OK=true
    break
  fi
done

if command -v adb &>/dev/null; then
  ADB_OK=true
fi

if $ADB_OK; then
  ok "ADB $(adb version 2>/dev/null | head -1)"
else
  if $CHECK_ONLY; then
    err "ADB not found"; flag_error "ADB / Android SDK"
  else
    info "Installing Android platform-tools via Homebrew…"
    brew install --quiet android-platform-tools
    export PATH="$(brew --prefix)/bin:$PATH"
    ok "ADB installed  →  $(adb version | head -1)"
  fi
fi

# Check device
echo ""
DEVICES=$(adb devices 2>/dev/null | grep -v "List of devices" | grep "device$" | awk '{print $1}')
if [[ -n "$DEVICES" ]]; then
  while IFS= read -r d; do
    ok "Device connected: $d"
  done <<< "$DEVICES"
else
  warn "No Android device connected. Connect device with USB debugging enabled before running tests."
fi

# ═══════════════════════════════════════════════════════════════════
# 6. Appium 3 + UiAutomator2 driver
# ═══════════════════════════════════════════════════════════════════
step "6/8  Appium 3 + UiAutomator2"

APPIUM_OK=false
if command -v appium &>/dev/null; then
  APPIUM_VER=$(appium -v 2>/dev/null)
  APPIUM_MAJOR=$(echo "$APPIUM_VER" | cut -d. -f1)
  if [[ "$APPIUM_MAJOR" -ge 3 ]]; then
    ok "Appium $APPIUM_VER"
    APPIUM_OK=true
  else
    warn "Appium $APPIUM_VER found — Forge requires Appium 3.x"
  fi
fi

if ! $APPIUM_OK; then
  if $CHECK_ONLY; then
    err "Appium 3 not found"; flag_error "Appium 3"
  else
    info "Installing Appium 3 globally…"
    npm install -g appium@latest
    ok "Appium $(appium -v) installed"
  fi
fi

# UiAutomator2 driver
UA2_OK=false
if command -v appium &>/dev/null; then
  # Drivers are installed into ~/.appium/node_modules/ (Appium's driver home)
  UA2_PKG="$HOME/.appium/node_modules/appium-uiautomator2-driver/package.json"
  if [[ -f "$UA2_PKG" ]]; then
    UA2_VER=$(python3 -c "import json; print(json.load(open('$UA2_PKG'))['version'])" 2>/dev/null || grep '"version"' "$UA2_PKG" | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
    ok "UiAutomator2 driver $UA2_VER"
    UA2_OK=true
  fi
fi

if ! $UA2_OK; then
  if $CHECK_ONLY; then
    err "UiAutomator2 driver not installed"; flag_error "UiAutomator2 driver"
  else
    info "Installing UiAutomator2 driver…"
    appium driver install uiautomator2 --source=npm
    ok "UiAutomator2 driver installed"
  fi
fi

# ═══════════════════════════════════════════════════════════════════
# 7. Forge dependencies
# ═══════════════════════════════════════════════════════════════════
step "7/8  Forge dependencies"

if ! $START_ONLY && ! $CHECK_ONLY; then
  # Maven (Java) deps
  info "Installing Maven dependencies (this may take a few minutes on first run)…"
  cd "$FORGE_ROOT"
  # On macOS, Homebrew JDK doesn't include system CA certs — use Keychain trust store
  MVN_SSL_OPTS=""
  if [[ "$(uname)" == "Darwin" ]]; then
    MVN_SSL_OPTS="-Djavax.net.ssl.trustStoreType=KeychainStore"
  fi
  set +e
  RAW_MVN_OUTPUT=$(mvn install -DskipTests --no-transfer-progress -q $MVN_SSL_OPTS 2>&1)
  MVN_EXIT=$?
  MVN_OUTPUT=$(echo "$RAW_MVN_OUTPUT" | grep -v "sun.misc.Unsafe\|lombok.permit.Permit\|terminally deprecated" || true)
  set -e
  if echo "$MVN_OUTPUT" | grep -q "PKIX\|certificate\|SSL\|TLS\|sun.security"; then
    err "Maven SSL error — likely caused by Zscaler certificate interception"
    echo ""
    echo -e "  ${YELLOW}Zscaler injects its own CA certificate for SSL inspection."
    echo -e "  The Homebrew JDK does not trust it by default.${RESET}"
    echo ""
    echo -e "  ${BOLD}Fix:${RESET} Import the Zscaler root certificate into the JDK truststore:"
    echo ""
    echo -e "  ${CYAN}  1. Export the Zscaler cert from macOS Keychain:${RESET}"
    echo -e "       Open Keychain Access → System Roots → find 'Zscaler' → Export as .pem"
    echo ""
    echo -e "  ${CYAN}  2. Import it into the JDK cacerts:${RESET}"
    echo -e "       sudo keytool -import -trustcacerts -alias zscaler \\"
    echo -e "         -file ~/zscaler.pem \\"
    echo -e "         -keystore \"\$JAVA_HOME/lib/security/cacerts\" \\"
    echo -e "         -storepass changeit -noprompt"
    echo ""
    echo -e "  ${CYAN}  3. Re-run:${RESET}  ./setup.sh"
    echo ""
    flag_error "Maven install (Zscaler SSL)"
  elif [[ $MVN_EXIT -ne 0 ]]; then
    err "mvn install failed"
    echo "$MVN_OUTPUT" | tail -10
    flag_error "Maven install"
  else
    ok "Maven dependencies installed"
  fi

  # Forge UI npm deps
  info "Installing Forge UI npm dependencies…"
  cd "$FORGE_ROOT/forge-ui"
  npm install --silent && ok "Forge UI npm dependencies installed" \
    || { err "forge-ui npm install failed"; flag_error "forge-ui npm install"; }

  # Forge MCP npm deps
  info "Installing Forge MCP npm dependencies…"
  cd "$FORGE_ROOT/forge-mcp"
  npm install --silent && ok "Forge MCP npm dependencies installed" \
    || { err "forge-mcp npm install failed"; flag_error "forge-mcp npm install"; }

  cd "$FORGE_ROOT"
else
  # Just verify node_modules exist
  [[ -d "$FORGE_ROOT/forge-ui/node_modules" ]]  && ok "Forge UI node_modules present" \
    || warn "Forge UI node_modules missing — run: cd forge-ui && npm install"
  [[ -d "$FORGE_ROOT/forge-mcp/node_modules" ]] && ok "Forge MCP node_modules present" \
    || warn "Forge MCP node_modules missing — run: cd forge-mcp && npm install"
  [[ -d "$FORGE_ROOT/target" ]]                  && ok "Maven target directory present" \
    || warn "Maven build missing — run: mvn install -DskipTests"
fi

# ═══════════════════════════════════════════════════════════════════
# 8. Claude CLI (optional — required for Chat tab)
# ═══════════════════════════════════════════════════════════════════
step "8/8  Claude CLI (for Chat tab)"

if command -v claude &>/dev/null; then
  ok "Claude CLI $(claude -v 2>/dev/null | head -1)"
else
  warn "Claude CLI not found — Chat tab will not work"
  info "Install from: https://claude.ai/download"
  info "Or via npm:   npm install -g @anthropic-ai/claude-code"
fi

# ═══════════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════════
sep
echo ""
if [[ ${#ERRORS[@]} -gt 0 ]]; then
  echo -e "${RED}${BOLD}  Setup completed with errors:${RESET}"
  for e in "${ERRORS[@]}"; do
    err "$e"
  done
  echo ""
  echo -e "${YELLOW}  Fix the above issues then re-run:  ${BOLD}./setup.sh${RESET}"
  echo ""
  exit 1
fi

if $CHECK_ONLY; then
  echo -e "${GREEN}${BOLD}  ✅ All checks passed — Forge is ready!${RESET}"
  sep
  exit 0
fi

# ═══════════════════════════════════════════════════════════════════
# Launch Forge UI
# ═══════════════════════════════════════════════════════════════════
sep
echo -e "${BOLD}${GREEN}  ✅ All dependencies installed!${RESET}"
sep
echo ""
echo -e "${BOLD}  Launching Forge UI…${RESET}"
echo ""
echo -e "  ${CYAN}URL:${RESET}  http://localhost:3847"
echo -e "  ${CYAN}Stop:${RESET} Ctrl+C"
echo ""

# Kill any existing instance
pkill -f "node server.js" 2>/dev/null || true
sleep 0.5

# Open browser after 2s (macOS)
if command -v open &>/dev/null; then
  (sleep 2 && open "http://localhost:3847") &
fi

cd "$FORGE_ROOT/forge-ui"
node server.js
