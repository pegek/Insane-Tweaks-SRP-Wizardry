#!/usr/bin/env bash
# Startuje serwer dedykowany z addonem w jednym z trzech wariantow (spec, sekcja 8), czeka na
# "Done", zatrzymuje go i wypisuje to, co jest w logu o addonie, plus bledy i ostrzezenia.
#
#   tools/server-check.sh <a|b|c> <katalog-roboczy>
#     a = Redux, b = Redux + Spore, c = Redux + Spore + Tombstone 9.1.4 + TombTweaks
#
# Zmienne: JAVA (domyslnie java, musi byc 17), TOMBTWEAKS_JAR (wymagany dla c).
# Jary cudzych modow ida do katalogu roboczego, NIGDY do repo.
set -euo pipefail

variant=${1:?wariant a, b albo c}
work=${2:?katalog roboczy}
java=${JAVA:-java}
forge=1.20.1-47.3.19
repo_root=$(cd "$(dirname "$0")/.." && pwd)

redux_url=https://api.modrinth.com/maven/maven/modrinth/electroblobs-wizardry-redux/0.8.9-forge/electroblobs-wizardry-redux-0.8.9-forge.jar
spore_url=https://api.modrinth.com/maven/maven/modrinth/fungal-infectionspore/2.2.0j/fungal-infectionspore-2.2.0j.jar
tombstone_url=https://www.cursemaven.com/curse/maven/corail-tombstone-243707/8606942/corail-tombstone-243707-8606942.jar

cache="$work/cache"
srv="$work/server-$variant"
mkdir -p "$cache" "$srv"

fetch() { # url plik
    [ -s "$cache/$2" ] || curl -sSfL --retry 3 -o "$cache/$2" "$1"
}

# Forge instalujemy raz, do wspolnego katalogu; kazdy wariant dostaje kopie bibliotek.
base="$work/forge-$forge"
if [ ! -f "$base/libraries/net/minecraftforge/forge/$forge/unix_args.txt" ]; then
    mkdir -p "$base"
    fetch "https://maven.minecraftforge.net/net/minecraftforge/forge/$forge/forge-$forge-installer.jar" installer.jar
    (cd "$base" && "$java" -jar "$cache/installer.jar" --installServer >install.log 2>&1)
fi
[ -d "$srv/libraries" ] || cp -r "$base/libraries" "$srv/"

rm -rf "$srv/mods" "$srv/logs" "$srv/world"
mkdir -p "$srv/mods"
fetch "$redux_url" redux.jar && cp "$cache/redux.jar" "$srv/mods/"
if [ "$variant" != a ]; then fetch "$spore_url" spore.jar && cp "$cache/spore.jar" "$srv/mods/"; fi
if [ "$variant" = c ]; then
    fetch "$tombstone_url" tombstone.jar && cp "$cache/tombstone.jar" "$srv/mods/"
    cp "${TOMBTWEAKS_JAR:?TOMBTWEAKS_JAR wymagany dla wariantu c}" "$srv/mods/"
fi
cp "$repo_root"/build/libs/ebreduxaddon-1.20.1-*.jar "$srv/mods/"

echo eula=true >"$srv/eula.txt"
printf 'online-mode=false\nspawn-protection=0\n' >"$srv/server.properties"

cd "$srv"
rm -f in.fifo && mkfifo in.fifo
# Trzymamy fifo otwarte do zapisu, inaczej serwer dostaje EOF na stdin od razu.
exec 3<>in.fifo
"$java" -Xmx3G @libraries/net/minecraftforge/forge/$forge/unix_args.txt nogui <in.fifo >server.out 2>&1 &
pid=$!

status=timeout
for _ in $(seq 1 300); do
    if grep -q 'Done (' server.out 2>/dev/null; then status=done; break; fi
    if ! kill -0 "$pid" 2>/dev/null; then status=crashed; break; fi
    sleep 2
done
if [ "$status" = done ]; then
    # Polecenia diagnostyczne, zanim serwer zejdzie.
    echo "forge mods" >&3 || true
    sleep 3
    echo stop >&3
fi
wait "$pid" || true
exec 3>&-

echo "== wariant $variant: $status"
grep -E '\[EbreduxAddon\]|ebreduxaddon' logs/latest.log | grep -v -i 'debug' | tail -40 || true
echo "== bledy i ostrzezenia (bez szumu znanego z Redux i Spore)"
grep -E '/(ERROR|WARN)\]|Exception' logs/latest.log \
    | grep -v -E 'halogen_light|Ambiguity between arguments|Incorrect key|is not correct. Correcting|uses unexpected schema|OFFLINE/INSECURE|authenticate usernames|hackers to connect|"online-mode"' \
    | tail -40 || true
[ "$status" = done ]
