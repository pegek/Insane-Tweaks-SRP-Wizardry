#!/usr/bin/env bash
# Wizualny test dymny klienta, bez ekranu: Xvfb + Mesa (llvmpipe). Wchodzi do swiata z datapackiem
# tools/smoke-pack (scena z rzeczami addonu), robi zrzuty ekranu i wypisuje z logu wszystko, co
# dotyczy addonu, plus brakujace modele/tekstury.
#
#   tools/client-smoke.sh <swiat-zrodlowy> <katalog-na-zrzuty>
#
# <swiat-zrodlowy>: dowolny zapis swiata 1.20.1 (np. world/ z serwera tools/server-check.sh).
# Wymaga: Xvfb, ImageMagick (import), Mesa. Dzwiek nie startuje (brak urzadzenia) - to oczekiwane.
# SMOKE_WITH_SPORE=1: zarazeni magowie i Mykomanta na scenie (Spore jest w dev zawsze od 0.3.1).
set -euo pipefail

src=${1:?swiat zrodlowy}
out=${2:?katalog na zrzuty}
repo=$(cd "$(dirname "$0")/.." && pwd)
display=:97
mkdir -p "$out"

rm -rf "$repo/run-smoke/saves/smoke"
mkdir -p "$repo/run-smoke/saves"
cp -r "$src" "$repo/run-smoke/saves/smoke"
rm -f "$repo/run-smoke/saves/smoke/session.lock"
mkdir -p "$repo/run-smoke/saves/smoke/datapacks"
cp -r "$repo/tools/smoke-pack" "$repo/run-smoke/saves/smoke/datapacks/ebtest"
pack="$repo/run-smoke/saves/smoke/datapacks/ebtest"
rm -f "$pack/setup_spore.mcfunction"
gradle_extra=()
if [ "${SMOKE_WITH_SPORE:-0}" = 1 ]; then
    gradle_extra=(-PwithSpore)
    cp "$repo/tools/smoke-pack/setup_spore.mcfunction" "$pack/data/ebtest/functions/setup_spore.mcfunction"
    sed -i 's/^say ebtest:setup done$/function ebtest:setup_spore\nsay ebtest:setup done/' "$pack/data/ebtest/functions/build.mcfunction"
fi
# Bez samouczka i ekranow powitalnych, mniejsze ustawienia dla programowego renderera.
cat >"$repo/run-smoke/options.txt" <<OPT
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
renderDistance:4
simulationDistance:5
guiScale:2
OPT

Xvfb $display -screen 0 1280x720x24 -ac -nolisten tcp &
xvfb=$!
trap 'kill $xvfb 2>/dev/null || true; pkill -f forgeclientuserdev 2>/dev/null || true' EXIT
sleep 2

log="$out/client.log"
(cd "$repo" && DISPLAY=$display LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 \
    bash gradlew --no-daemon --max-workers=2 "${gradle_extra[@]}" runSmokeClient >"$log" 2>&1) &

for _ in $(seq 1 240); do
    grep -q 'ebtest:setup done' "$log" 2>/dev/null && break
    sleep 5
done
sleep 25
DISPLAY=$display import -window root "$out/scene.png"
sleep 10
DISPLAY=$display import -window root "$out/scene2.png"

echo "== addon i zasoby w logu klienta"
grep -v '/DEBUG\]' "$log" | grep -E 'ebreduxaddon|ebtest|Missing textures|Unable to load model|Exception|Unknown function|Failed to load function' \
    | grep -v -E 'Mod file|Reflective setAccessible|io.netty' | tail -40 || true
