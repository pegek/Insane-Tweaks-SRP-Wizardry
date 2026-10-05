# Druga polowa sceny (patrz setup.mcfunction): chunki sa juz zaladowane przez forceload.
fill -7 149 -3 7 149 12 minecraft:smooth_stone
fill -7 150 -3 7 158 12 minecraft:air
fill -2 150 8 2 153 8 minecraft:polished_andesite
summon minecraft:armor_stand -2 150 5 {Rotation:[180f,0f],ShowArms:1b,NoGravity:1b,ArmorItems:[{id:"ebreduxaddon:grafted_boots",Count:1b},{id:"ebreduxaddon:grafted_leggings",Count:1b},{id:"ebreduxaddon:grafted_chestplate",Count:1b},{id:"ebreduxaddon:grafted_helmet",Count:1b}]}
summon minecraft:armor_stand 2 150 5 {Rotation:[180f,0f],ShowArms:1b,NoGravity:1b,ArmorItems:[{id:"ebreduxaddon:sentient_boots",Count:1b},{id:"ebreduxaddon:sentient_leggings",Count:1b},{id:"ebreduxaddon:sentient_chestplate",Count:1b},{id:"ebreduxaddon:sentient_helmet",Count:1b}]}
summon minecraft:item_frame -1 151 7 {Facing:2b,Fixed:1b,Item:{id:"ebreduxaddon:symbiotic_wand",Count:1b}}
summon minecraft:item_frame 1 151 7 {Facing:2b,Fixed:1b,Item:{id:"ebreduxaddon:sentient_wand",Count:1b}}
summon ebreduxaddon:spine 0 151.6 3 {NoGravity:1b}
tp @a 0 150 0 0 10
say ebtest:setup done
