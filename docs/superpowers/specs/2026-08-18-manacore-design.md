# manacore — zunifikowany system many gracza (spec v1)

Data: 2026-08-18
Status: zatwierdzony projekt, przed planem implementacji
Zakres: **v1 = rdzeń + most do Electroblob's Wizardry + most do Trinkets and Baubles.** Fazy 4–7 opisane na końcu jako odłożone.

## 1. Cel

Jedna pula many gracza, będąca **jedynym źródłem prawdy**, zastępująca zarówno manę różdżki EBW (w kontekście czarowania), jak i pulę `MagicStats` z Trinkets and Baubles. Jeden pasek, jedna liczba, jeden regen.

Punktem wyjścia jest mod `player_mana` 1.2.1 (w packu DEv 1.2 obecny jako `.disabled`), ale system jest pisany od zera, z trzema zasadniczymi różnicami wobec pierwowzoru — patrz §3.

Nowy, ósmy subprojekt: **modid `manacore`, grupa `com.spege.manacore`.** Uzasadnienie routingu (reguła z `CLAUDE.md`): to nie jest gameplay `insanetweaks` ani glue do DEv 1.2, tylko narzędzie ogólnego przeznaczenia. Zero zależności od contentu.

## 2. Ustalenia z rozpoznania (zweryfikowane na bajtkodzie)

Wersje badane: EBW 4.3.19, Trinkets and Baubles 0.33.1 (`libs/`; pack ma 0.33.3 — **drift do zamknięcia przed implementacją**), PotionCore 1.9, wizardryutils 1.3.1, player_mana 1.2.1.

**2.1. `SpellCastEvent$Post` odpala się wyłącznie po udanym caście.** `ItemWand.cast`: `Spell.cast()` → `ifeq` (false = wyjście) → dopiero potem konstrukcja i `EventBus.post` eventu `Post` → dopiero potem `consumeMana` różdżki. To jest podstawa całego mostu do EBW.

**2.2. Kolejność bramek w `ItemWand.canCast`:** post `Pre` (lub `Tick` dla ciągłych) → jeśli anulowany, `return false` → **dopiero potem** bramka `getCost(...) > getMana(stack)` → potem `tier czaru <= tier różdżki` → potem cooldown. Nasz handler `Pre` biegnie przed bramką EBW na manie różdżki, ale ta bramka nadal obowiązuje.

**2.3. `IManaStoringItem` nie zna właściciela stacka:**

```
int  getMana(ItemStack)                             <- brak gracza
void setMana(ItemStack, int)                        <- brak gracza
int  getManaCapacity(ItemStack)                     <- brak gracza
void rechargeMana(ItemStack, int)                   <- brak gracza
void consumeMana(ItemStack, int, EntityLivingBase)  <- gracz JEST
```

Dlatego „mana itemu jako okno na pulę gracza" nie jest wykonalne na poziomie interfejsu (stack może leżeć w skrzyni, w Arcane Workbench, na ziemi). Każde przepięcie musi iść przez punkt wywołania.

**2.4. Stan many w packu (skan 285 jarów, z `.disabled` włącznie).** Poza Trinkets and Baubles **żaden aktywny mod nie trzyma puli many per-gracz**. Ars Magica 2 (`IEntityExtension`: `getCurrentMana`/`deductMana`/`getMaxMana`, drugi zasób „burnout", mana-linki, mana battery) jest wyłączony; `player_mana` również. Cała reszta packa operuje na per-item `IManaStoringItem`: Ancient Spellcraft (mana artefacts, everfull flask, ring of mana transfer, mana vortex/flare), SpellBundle (`ring_bonus_mana`, bonus setowy zbroi na regen many, własne paski many per-żywioł rysowane w namespace `xat`), ArcaneApprentices, Necromancer's Delight (mana leech), handler `wizardryutils`, oraz nasz własny `ItemZhonyasHourglassArtefact`.

**2.5. `wizardryutils` już rejestruje atrybuty gracza modyfikujące czary.** `com.windanesz.wizardryutils.server.Attributes`: globalne `COST`, `POTENCY`, `CHARGEUP`, `CONDENSING`, `DURATION`, `BLAST`, `RANGE`, `COOLDOWN` oraz warianty per-żywioł (`FIRE_COST`, `ICE_COST`, `NECROMANCY_COST`, …), typu `RangedAttribute`. Nasz resolver kosztu **musi je respektować**, inaczej powstanie drugi, konkurencyjny system kosztów w tym samym packu.

**2.6. Ancient Spellcraft `PotionManaRegeneration`** (`ancientspellcraft:mana_regeneration`) leje manę do *trzymanego itemu*, nie do gracza. Kandydat do przepięcia — faza 5.

**2.7. Korekty do wstępnych założeń:** PotionCore 1.9 nie zawiera ani jednej klasy związanej z maną (ma `PotionMagicFocus`/`PotionMagicInhibition`, ale one nie dotykają zasobu). QualityTools — `quality.arcane` to nazwa poziomu jakości, czysta kosmetyka; „manapool jako quality" to robota od zera, nie integracja. `spellarchives` wyświetla w GUI `Cost: %d mana` — po zmianie źródła kosztu ten napis skłamie.

## 3. Trzy różnice wobec `player_mana`

1. **Mana pobierana po udanym caście, nie przed.** `player_mana` odejmuje w `SpellCastEvent$Pre`, który leci z `canCast()` — czyli *zanim* `Spell.cast()` w ogóle spróbuje. Mana znika, gdy czar się nie odpalił. U nas `Pre` jest wyłącznie bramką, odjęcie następuje w `Post`.
   *Ograniczenie do świadomego przyjęcia:* `cast()==false` łapie czary, które nie miały czego zrobić (Heal na pełnym HP, czar bez celu, blok poza zasięgiem). Nie złapie czaru, który poleciał w powietrze i nie trafił — dla EBW to sukces i żaden hook tego nie odróżni.
2. **`maxMana` jest atrybutem, nie zapisaną liczbą** (§4.2).
3. **Progresja z samego kastowania jest symboliczna i twardo capowana** — u `player_mana` to główne źródło wzrostu puli i nagradza wyłącznie pasywne granie. Właściwe źródła progresji projektuje faza 6.

## 4. Architektura rdzenia

### 4.1. Capability

`IManaPool` na graczu przechowuje **dokładnie dwie liczby**:
- `current` (double) — bieżąca mana,
- `progressionBonus` (double) — trwały przyrost maksimum (kastowanie, Mana Crystal, przyszłe źródła permanentne).

`maxMana` **nie jest przechowywana nigdy** — jest liczona (§4.2).

### 4.2. Atrybut `MAX_MANA`

`RangedAttribute("manacore.maxMana", base, 0, hardCap)` rejestrowany na `EntityLivingBase`. `maxMana = attribute.getAttributeValue()`, clampowane configowym sufitem.

Każde źródło bonusu to `AttributeModifier`:
- `progressionBonus` → jeden modyfikator o stałym UUID, przeliczany przy każdej zmianie (wzorzec `UpdatingAttribute` z TaB),
- źródła fazy 6 (bauble EBW, enchant zbroi, quality, poziom drzewka Reskillable, achievement) → własne UUID, **bez żadnej nowej logiki w rdzeniu**.

To jest główna stawka projektu: fazy 6 i 7 redukują się do listy modyfikatorów i jednego clampa zamiast sześciu niezależnych systemów z własnymi capami.

### 4.3. Sieć

Dwa pakiety: **pełna synchronizacja** (login, zmiana wymiaru, respawn) i **delta** przy zmianie `current`. Klient trzyma mirror wyłącznie na potrzeby HUD-u; nigdy nie jest źródłem prawdy.

Zgodnie z regułami side-safety z `CLAUDE.md`: klasa handlera pakietu **nie może** nosić `@SideOnly` (`registerMessage` woła `newInstance()` po obu stronach); praca klienta idzie do osobnej klasy `@SideOnly(Side.CLIENT)` wołanej jednym `invokestatic` po `if (ctx.side != Side.CLIENT) return null;`.

### 4.4. HUD

`RenderGameOverlayEvent$Post`, konfigurowalne X/Y, opcjonalna liczba obok paska. **Tekstury robocze przejęte z `player_mana`** (`bar_mana.png`) — praca jest prywatna, docelowe assety to osobne zadanie.

Cały render rejestrowany z `ClientProxy`, nigdy z klasy `@Mod` (reguła: `registerEntityRenderingHandler` i pokrewne wymuszają rozwiązanie typów `fml.client` przy ładowaniu klasy `@Mod`).

## 5. Most do EBW

### 5.1. Cykl kosztu

| zdarzenie | działanie |
|---|---|
| `Pre` (`Source.WAND`) | policz koszt; `current < koszt` → `setCanceled(true)` + komunikat na action barze. **Nie odejmuj.** |
| `Post` | **odejmij koszt.** Wyłącznie po stronie serwera. |
| `Tick` | czary ciągłe: koszt per-tick; brak many → cancel (przerywa kanał) |
| `Finish` | rozliczenie końcowe czaru ciągłego, w tym jego refund |

Refund ze `storage` (§5.3) naliczany jest **w `Post` dla czarów jednorazowych i w `Finish` dla ciągłych** — nigdy per-tick, żeby nie zamienić refundu w regen.

**Źródła płacące z puli w v1: wyłącznie `Source.WAND`.** Zwoje, dyspensery, komendy i NPC (Sim Wizardy, uczniowie z ArcaneApprentices, czarownicy EBW) czarują jak dotąd — żaden mob nie zablokuje się na pustej puli, której nikt mu nie regeneruje.

### 5.2. Resolver kosztu

```
koszt = Spell.getCost()
      * SpellModifiers.get(COST)
      * config.costMultiplier
      * modyfikator wizardryutils (COST oraz <element>_COST, jeśli mod obecny)
```

Integracja z `wizardryutils` jest **soft**: `Loader.isModLoaded` + wyszukanie atrybutu po nazwie, zero zależności kompilacyjnej.

### 5.3. Mana różdżki i trzy upgrade'y

Dwa `@Redirect`, oba z właścicielem `ItemWand` (spełniają regułę exact-receiver):
- `getMana` wewnątrz `canCast` → zwraca `getManaCapacity(stack)`, czyli bramka `koszt > mana różdżki` przepuszcza wszystko, co mieści się w pojemności różdżki (a nie `Integer.MAX_VALUE` — dzięki temu limit tieru różdżki nadal działa jako sufit pojedynczego czaru),
- `consumeMana` wewnątrz `cast` → no-op.

Przechowywana w NBT mana różdżki **pozostaje nietknięta**, więc odinstalowanie moda wraca do stanu wyjściowego.

Przemapowanie upgrade'ów:
- **`condenser`** → +regen naszej puli,
- **`siphon`** → mana do naszej puli za zabójstwo,
- **`storage`** → **mana refund %**: czytamy `getManaCapacity` różdżki i konwertujemy na procent many zwracanej natychmiast po udanym caście. Wyższy tier i więcej `storage` = większy zwrot. Wybrane zamiast redukcji kosztu, bo nie dokłada czwartego mnożnika do statystyki, którą już manipulujemy z trzech stron (config, `wizardryutils`, modyfikatory czaru), i capuje się naturalnie (refund nigdy > 100%).

### 5.4. Zasięg — reguła twarda

**Ruszamy wyłącznie `ItemWand` i podklasy.** Każdy inny `IManaStoringItem` — artefakty EBW i Ancient Spellcraft, pierścienie, flaszki, `ItemZhonyasHourglassArtefact` — zachowuje własną pulę i działa bez zmian. To jest decyzja projektowa, nie tymczasowe uproszczenie.

## 6. Most do Trinkets and Baubles

Mixiny na `xzeroair.trinkets.capabilities.magic.MagicStats`: `getMana`, `setMana`, `addMana`, `spendMana`, `getMaxMana`, `refillMana`, `needMana` → delegacja do naszej puli, z **konfigurowalnym przelicznikiem jednostek** (TaB liczy w `float` w innej skali niż nasz `double`).

HUD TaB wyłączany (`ConfigManaBarHud`; jeśli config okaże się niewystarczający — mixin na `ManaHud`).

Itemy: `Mana_Crystal` → trwały `+max` przez modyfikator atrybutu (capowany), `Mana_Reagent` i `Mana_Candy` → doładowanie `current`.

## 7. API, config, konwencje

`com.spege.manacore.api.ManaAPI` — statyczna fasada (`get`, `getMax`, `spend`, `add`, `addMaxModifier`), żeby `insanetweaks` i `reskilltweaks` sięgały bez refleksji.

`insanetweaks.util.PlayerManaCompat` (refleksyjny most do `player_mana`) **zostaje nietknięty w v1.** Jego wymiana to osobne zadanie po v1.

Config `manacore.cfg`: pola są obiektami kategorii, więc `@Config(modid = "manacore", category = "")` — z własnym handlerem `OnConfigChangedEvent` → `ConfigManager.sync`.

Checklista ekstrakcji (ósma z rzędu): **`src/main/resources/pack.mcmeta` z `pack_format: 3`** — bez niego `assets/manacore/lang/*.lang` nigdy nie stanie się domeną zasobów i każdy klucz wyrenderuje się jako własna nazwa, bez śladu w logach. Wersja w trzech miejscach: `build.gradle`, stała `VERSION` w klasie `@Mod`, `mcmod.info` (albo wyprowadzenie dwóch ostatnich z `version`, jak w `reskilltweaks`).

## 8. Świadome straty i długi

- **Bonusy rasowe TaB przepadają w v1.** Nadpisanie `MagicStats.getMaxMana` kasuje `magicAffinity` i `racialAffinity`. Gracz rasy magicznej straci bonus i nie dowie się dlaczego. Odtworzenie — faza 6, jako źródło zaprojektowane pod nasz balans, nie dziedziczone z TaB.
- **Efekty mana-owe innych modów przez jeden etap nie działają na naszej puli.** ASC `PotionManaRegeneration`, bonus setowy zbroi SpellBundle, pętla ArcaneApprentices, mana leech z Necromancer's Delight — wszystkie ładują lub drenują manę *itemu*. Ponieważ artefakty zachowują własne pule (§5.4), realnie martwe jest tylko to, co celuje w **różdżkę**. Spłata: faza 5.
- **`spellarchives` będzie pokazywał koszt z niewłaściwego źródła** (`gui.spellarchives.cost_fmt`). Do zsynchronizowania w fazie 4 lub 5.

## 9. Znane niewiadome do potwierdzenia w implementacji

1. **Dokładna forma obu `@Redirect` na `ItemWand`** — offsety i deskryptory potwierdzone (`getMana` w `canCast`, `consumeMana` w `cast`), ale sygnatury handlerów i nazwy SRG trzeba potwierdzić `javap -p -c` z przypisaniem invoke do metody-właściciela, nie przez `grep -A`.
2. **Czy `SpellCastEvent$Post` leci po obu stronach** — odjęcie musi być bezwarunkowo strażowane `!world.isRemote`.
3. **Skala jednostek TaB ↔ nasza** — przelicznik trzeba wyznaczyć empirycznie po odczytaniu domyślnych wartości `EntityManaConfig`.
4. **Czy `ConfigManaBarHud` wystarcza do wyłączenia HUD-u TaB**, czy potrzebny mixin na `ManaHud`.
5. **Czy `ItemWand` ma w packu podklasy z innych modów** (Ancient Spellcraft, SpellBundle), które reguła §5.4 obejmie nieumyślnie albo pominie.
6. **Ścieżka czarów ciągłych** — `canCast` posyła `Pre` przy pierwszym ticku i `Tick` przy kolejnych, ale nie jest potwierdzone, czy `Post` w ogóle leci dla `isContinuous`, czy cały cykl obsługują `Tick` i `Finish`. Do potwierdzenia na `ItemWand.onUsingTick` i `onPlayerStoppedUsing` **przed** napisaniem handlerów — od tego zależy, gdzie ląduje odjęcie kosztu początkowego.

### Rozstrzygnięte domyślne zachowania

**Śmierć i respawn:** `current` zeruje się przy śmierci, `progressionBonus` jest zachowywany **zawsze** (także bez `keepInventory`) — trwały dorobek nie może przepaść przez jeden zgon. Oba zachowania configowalne, powyższe to wartości domyślne.

## 10. Poza zakresem v1 (zapisane, żeby nie zginęło)

- **Faza 4 — normalizacja kosztów** między EBW a TaB, żeby oba mody miały porównywalnie zbalansowane koszty many.
- **Faza 5 — agregacja cudzych efektów**: przepięcie ASC `PotionManaRegeneration`, bonusu setowego SpellBundle, mana leech z Necromancer's Delight i pętli ArcaneApprentices na naszą pulę.
- **Faza 6 — źródła progresji**: poziom drzewka „magic" w Reskillable, achievementy, właściwość `+max mana` na artefaktach (baubles) EBW, enchant na zbroję, nowe „quality" w QualityTools, odtworzenie bonusów rasowych. Wszystkie jako `AttributeModifier` na `MAX_MANA`. `SetBonus` jest gotowym wektorem dostarczania.
- **Faza 7 — system capów**: wspólny sufit i reguły składania bonusów z wielu źródeł.

## 11. Kryteria akceptacji v1

1. Czar, który się nie odpalił (`cast()==false`), **nie kosztuje many** — weryfikowane na Heal przy pełnym HP.
2. Czar udany kosztuje manę dokładnie raz, wyłącznie po stronie serwera, a klient widzi zmianę na pasku.
3. Czar ciągły pobiera manę per-tick i **przerywa się**, gdy pula się wyczerpie.
4. Pusta pula blokuje rzucenie z komunikatem; pusta mana różdżki **nie blokuje niczego**.
5. Mana różdżki w NBT nie zmienia się w trakcie gry.
6. `storage` na różdżce daje mierzalny refund; `condenser` przyspiesza regen puli; `siphon` daje manę za zabójstwo.
7. Pula i pasek TaB pokazują **tę samą liczbę** co nasze, a HUD TaB nie rysuje drugiego paska.
8. Artefakt EBW/ASC z własną maną działa dokładnie jak przed instalacją moda.
9. `cleanmix.log` z czystego startu: wszystkie oczekiwane linie `APPLY`, zero `InvalidInjectionException`, zero `Scanned 0`.
10. Serwer dedykowany startuje i pozwala czarować (weryfikacja side-safety — czysty start klienta niczego tu nie dowodzi).
