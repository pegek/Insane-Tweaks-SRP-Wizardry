# Dokladane do sceny tylko przy SMOKE_WITH_SPORE=1 (tools/client-smoke.sh): bez Spore ten typ
# encji nie istnieje, a funkcja z nieznanym id nie wczytalaby sie wcale.
summon ebreduxaddon:infected_wizard 0 150 6 {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}
summon ebreduxaddon:infected_wizard -4 150 7 {NoAI:1b,Rotation:[150f,0f],PersistenceRequired:1b}
summon ebreduxaddon:infected_wizard 4 150 7 {NoAI:1b,Rotation:[210f,0f],PersistenceRequired:1b}
