# Scena testu wizualnego: dwa stojaki w zbrojach, dwie ramki z rozdzkami, kolec w powietrzu,
# gracz z rozdzka (HUD zaklecia) i efektem Cleansing (ikona efektu). Uruchamiane raz na gracza.
tag @s add eb_setup
gamemode creative @s
time set noon
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
fill -7 149 -3 7 149 12 minecraft:smooth_stone
fill -7 150 -3 7 158 12 minecraft:air
fill -2 150 8 2 153 8 minecraft:polished_andesite
tp @s 0 150 0 0 10
summon minecraft:armor_stand -2 150 5 {Rotation:[180f,0f],ShowArms:1b,NoGravity:1b,ArmorItems:[{id:"ebreduxaddon:grafted_boots",Count:1b},{id:"ebreduxaddon:grafted_leggings",Count:1b},{id:"ebreduxaddon:grafted_chestplate",Count:1b},{id:"ebreduxaddon:grafted_helmet",Count:1b}]}
summon minecraft:armor_stand 2 150 5 {Rotation:[180f,0f],ShowArms:1b,NoGravity:1b,ArmorItems:[{id:"ebreduxaddon:sentient_boots",Count:1b},{id:"ebreduxaddon:sentient_leggings",Count:1b},{id:"ebreduxaddon:sentient_chestplate",Count:1b},{id:"ebreduxaddon:sentient_helmet",Count:1b}]}
summon minecraft:item_frame -1 151 7 {Facing:2b,Fixed:1b,Item:{id:"ebreduxaddon:symbiotic_wand",Count:1b}}
summon minecraft:item_frame 1 151 7 {Facing:2b,Fixed:1b,Item:{id:"ebreduxaddon:sentient_wand",Count:1b}}
summon ebreduxaddon:spine 0 151.6 3 {NoGravity:1b}
item replace entity @s weapon.mainhand with ebreduxaddon:symbiotic_wand{spells:["ebreduxaddon:cleanse","ebreduxaddon:spine_volley","ebreduxaddon:grasp","ebreduxaddon:purifying_pulse"]}
item replace entity @s hotbar.1 with ebreduxaddon:sentient_wand{spells:["ebreduxaddon:grasp","ebreduxaddon:purifying_pulse"]}
item replace entity @s hotbar.2 with ebreduxaddon:grafted_chestplate
item replace entity @s hotbar.3 with ebreduxaddon:sentient_helmet
effect give @s ebreduxaddon:cleansing 600 0
say ebtest:setup done
