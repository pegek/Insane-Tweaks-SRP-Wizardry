# Scena testu wizualnego: dwa stojaki w zbrojach, dwie ramki z rozdzkami, kolec w powietrzu,
# gracz z rozdzka (HUD zaklecia) i efektem Cleansing (ikona efektu). Uruchamiane raz na gracza.
# Budowa sceny idzie osobno (ebtest:build) po 2 s: w swiecie ze spawnem daleko od (0,0) fill i summon
# w tym samym ticku co forceload trafialy w niezaladowane chunki, a gracz spadal do oceanu.
tag @s add eb_setup
gamemode creative @s
time set noon
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
forceload add -16 -16 15 15
tp @s 0 150 0 0 10
item replace entity @s weapon.mainhand with ebreduxaddon:symbiotic_wand{spells:["ebreduxaddon:cleanse","ebreduxaddon:spine_volley","ebreduxaddon:grasp","ebreduxaddon:purifying_pulse"]}
item replace entity @s hotbar.1 with ebreduxaddon:sentient_wand{spells:["ebreduxaddon:grasp","ebreduxaddon:purifying_pulse"]}
item replace entity @s hotbar.2 with ebreduxaddon:grafted_chestplate
item replace entity @s hotbar.3 with ebreduxaddon:sentient_helmet
effect give @s ebreduxaddon:cleansing 600 0
schedule function ebtest:build 40t
