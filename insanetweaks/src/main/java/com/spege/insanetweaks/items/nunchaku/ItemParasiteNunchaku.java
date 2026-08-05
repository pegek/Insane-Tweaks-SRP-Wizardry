package com.spege.insanetweaks.items.nunchaku;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mujmajnkraft.bettersurvival.items.ItemNunchaku;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.GearCategory;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.item.tool.IHaveReach;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfig;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigSystems;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;

/**
 * Pasożytnicze nunchaku — Living i Sentient w jednej klasie, rozróżniane przez {@link ParasiteTier}.
 *
 * <p><b>Dlaczego dziedziczymy akurat po {@link ItemNunchaku}:</b> Better Survival rozpoznaje
 * nunchaku przez {@code instanceof ItemNunchaku} w czterech miejscach (spin w ModClientHandler,
 * spin i combo w CommonEventHandler, EnchantmentSpecialBonus). Bez tego broń się nie kręci i nie
 * zbiera combo. Java ma pojedyncze dziedziczenie, więc drugiej strony —
 * {@code WeaponToolMeleeBase} z SRParasites — nie da się odziedziczyć i przepisujemy ją tutaj.
 *
 * <p><b>Progi czytamy z configu SRParasites</b> ({@code SRPConfig}, {@code SRPConfigSystems}),
 * nie duplikujemy ich u siebie — inaczej przekręcenie balansu SRP odkleiłoby tę broń od rodziny.
 */
public class ItemParasiteNunchaku extends ItemNunchaku implements IHaveReach {

    /** Zasięg jak u miecza SRP ({@code addReach = 1} w konstruktorze WeaponMeleeSword). */
    private static final float ADDED_REACH = 1.0F;

    /** Odstęp między sprawdzeniami ewolucji. Tyle samo, ile ma natywne WeaponToolMeleeBase. */
    private static final int EVOLUTION_CHECK_INTERVAL = 80;

    /** Odstęp między próbami nałożenia Prey. Też z natywnego WeaponToolMeleeBase. */
    private static final int PREY_CHECK_INTERVAL = 40;

    /** Czas trwania Prey w tickach — natywna wartość. */
    private static final int PREY_DURATION = 1200;

    /**
     * Mianownik rzutu na Prey: trafienie zachodzi przy {@code nextInt(N) == 0}, czyli z szansą
     * 1/N — tu <b>1%</b>, nie 100%. Natywna wartość z {@code WeaponToolMeleeBase}.
     *
     * <p>🚨 Nazwa mówi „ONE_IN" celowo. Poprzednia (`PREY_CHANCE_PERCENT = 100`) zapraszała do
     * „poprawienia" warunku na {@code nextInt(100) < 100}, co dałoby Prey w KAŻDEJ próbie, czyli
     * co 2 sekundy zamiast raz na ~3,3 minuty.
     */
    private static final int PREY_ONE_IN = 100;

    /**
     * Wymiar, z którego SRP czyta poziom rozwoju zarazy.
     *
     * <p>🚨 222 to liczba ZASZYTA w {@code WeaponToolMeleeBase.onUpdate} — nie jest to wymiar,
     * w którym stoi gracz, tylko stały klucz, pod którym SRP trzyma globalny stan rozwoju.
     * Powtarzamy ją świadomie, żeby nasza broń widziała dokładnie to samo co natywna. Nie
     * podmieniaj tego na {@code world.provider.getDimension()} — rozjechałoby to nas z rodziną.
     */
    private static final int SRP_DEVELOPMENT_SAVE_ID = 222;

    private final ParasiteTier tier;

    public ItemParasiteNunchaku(Item.ToolMaterial material, ParasiteTier tier) {
        super(material);
        this.tier = tier;
        // ItemCustomWeapon zawolal juz setMaxDamage(material.getMaxUses()) - NADPISUJEMY ten wynik,
        // a nie dokladamy drugiej wartosci.
        double durabilityMultiplier = ModConfig.gear.nunchaku.durabilityMultiplier;
        setMaxDamage((int) Math.max(1.0D, material.getMaxUses() * durabilityMultiplier));
    }

    public ParasiteTier getTier() {
        return tier;
    }

    /** Konsumowane przez {@code SRPEventHandlerBus} — sprawdzone, działa dla dowolnego itemu. */
    @Override
    public float getReach() {
        return ADDED_REACH;
    }

    /**
     * Efekty pasożytnicze plus licznik ewolucji.
     *
     * <p>Licznik rośnie <b>tylko gdy cel zginął</b> i o jego {@code getMaxHealth()} — dokładnie
     * jak {@code WeaponToolMeleeBase.hitEntity}. To dlatego gruby pasożyt liczy się za setkę
     * zombie. Nie zamieniaj tego na „obrażenia zadane": rozjechałoby to metrykę z resztą rodziny.
     */
    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        boolean result = super.hitEntity(stack, target, attacker);

        if (target.world.isRemote) {
            return result;
        }

        ParasiteNunchakuEffects.applyOnHit(target, tier);

        if (target.getHealth() <= 0.0F) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            long current = tag.getInteger("srpkills");
            // Maks. HP przycinamy PRZED dodaniem, nie po. Rzutowanie (long) Float.MAX_VALUE
            // saturuje sie do Long.MAX_VALUE, wiec przy niepustym liczniku samo dodawanie
            // przekreca sie na ujemne i Math.min przepuszcza smiec - ewolucja nie zaszlaby nigdy.
            // Mody uzywaja Float.MAX_VALUE jako pseudo-niesmiertelnosci, wiec to nie jest teoria.
            long gained = (long) Math.min(target.getMaxHealth(), (float) Integer.MAX_VALUE);
            long added = current + gained;
            tag.setInteger("srpkills", (int) Math.min(added, (long) Integer.MAX_VALUE));
        }

        return result;
    }

    /**
     * Ewolucja i Prey. Ta sama ścieżka, z której korzysta broń natywna.
     *
     * <p>Świadomie NIE wieszamy tego na {@code LivingUpdateEvent} — {@code onUpdate} odpala się
     * tylko dla przedmiotów w ekwipunku, a wieszanie logiki na hakach per-encja jest w tym
     * projekcie udokumentowanym źródłem lagów.
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity holder, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, holder, itemSlot, isSelected);

        if (world.isRemote || !(holder instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) holder;

        if (tier.isCalling() && holder.ticksExisted % PREY_CHECK_INTERVAL == 0) {
            tryApplyPrey(living, world);
        }

        if (holder.ticksExisted % EVOLUTION_CHECK_INTERVAL == 0) {
            tryEvolve(stack, living, itemSlot);
        }
    }

    /** Kalka bloku {@code calling} z natywnego {@code WeaponToolMeleeBase.onUpdate}. */
    private void tryApplyPrey(EntityLivingBase living, World world) {
        if (!ModConfig.interactions.enableParasiteNunchakuPrey || !SRPConfigSystems.useScent) {
            return;
        }
        if (world.rand.nextInt(PREY_ONE_IN) != 0) {
            return;
        }
        SRPSaveData data = SRPSaveData.get(world, SRP_DEVELOPMENT_SAVE_ID);
        if (data == null || data.getDeveLevel() < SRPConfigSystems.deveScentUse) {
            return;
        }
        living.addPotionEffect(new PotionEffect(SRPPotions.PREY_E, PREY_DURATION, 0, false, false));
    }

    /**
     * Podmiana Living → Sentient przy progu z configu SRP.
     *
     * <p>NBT wędruje w całości (enchanty, nazwa). Licznik zerujemy, żeby ewentualny kolejny
     * stopień startował od zera.
     */
    private void tryEvolve(ItemStack stack, EntityLivingBase living, int itemSlot) {
        if (tier != ParasiteTier.LIVING) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || tag.getInteger("srpkills") <= SRPConfig.weapon_livingSentient_HP_needed) {
            return;
        }
        Item evolved = ParasiteNunchakuItems.sentient();
        if (evolved == null) {
            return;
        }

        NBTTagCompound carried = tag.copy();
        carried.setInteger("srpkills", 0);

        ItemStack evolvedStack = new ItemStack(evolved);
        evolvedStack.setTagCompound(carried);
        // Wytrzymalosc przenosimy PROPORCJONALNIE, a nie co do punktu: oba tiery moga miec inny
        // maxDamage, wiec skopiowanie surowej wartosci zrobiloby z nadgryzionej broni zlom albo
        // wyleczyloby ja za darmo.
        if (stack.getMaxDamage() > 0 && evolvedStack.getMaxDamage() > 0) {
            double wear = (double) stack.getItemDamage() / (double) stack.getMaxDamage();
            evolvedStack.setItemDamage((int) (wear * evolvedStack.getMaxDamage()));
        }

        int targetSlot = resolveInventorySlot(living, stack, itemSlot);
        if (targetSlot < 0) {
            // Nie potrafimy wskazac slotu na pewno - zostawiamy Living w spokoju. Sprobujemy
            // ponownie za 80 tickow, a do tego czasu gracz nic nie traci.
            return;
        }

        // 🚨 NIE wolno tu wolac stack.shrink(1) przed podmiana. replaceItemInInventory NADPISUJE
        // slot, wiec kasowanie starego stacka byloby zbedne - a gdyby podmiana sie nie powiodla,
        // gracz zostalby z pusta reka i strata broni. Kasujemy WYLACZNIE po potwierdzeniu.
        if (!living.replaceItemInInventory(targetSlot, evolvedStack)) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks] parasite nunchaku: could not place the evolved weapon in slot {} "
                            + "of {} - leaving the Living one alone",
                    Integer.valueOf(targetSlot), living.getName());
            return;
        }

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] parasite nunchaku: evolved into Sentient for {}",
                living.getName());
    }

    /**
     * Zamienia {@code itemSlot} z {@code onUpdate} na indeks, który rozumie
     * {@code replaceItemInInventory} — albo −1, gdy nie da się tego zrobić bezpiecznie.
     *
     * <p>🚨 TO NIE JEST NADGORLIWOSC. {@code itemSlot} z {@code onUpdate} NIE jest jednoznacznym
     * indeksem: {@code InventoryPlayer.decrementAnimations} iteruje TRZY osobne listy
     * (główna 36, zbroja 4, offhand 1) i licznik {@code i} startuje od zera dla każdej z nich.
     * Broń leżąca w offhandzie dostaje więc {@code itemSlot = 0} — a
     * {@code EntityPlayer.replaceItemInInventory(0, …)} pisze do slotu 0 HOTBARU.
     *
     * <p>Bez tego rozstrzygnięcia ewolucja w offhandzie kasowała zawartość slotu 0, zostawiała
     * Living tam, gdzie był (z nietkniętym licznikiem, bo zerowana jest kopia NBT) i powtarzała
     * się co 80 ticków, produkując Sentienta w nieskończoność.
     *
     * <p>Rozpoznajemy po TOŻSAMOŚCI referencji — {@code onUpdate} dostaje ten sam obiekt stacka,
     * który leży w slocie, więc {@code ==} jest tu właściwym narzędziem, nie {@code equals}.
     */
    private static int resolveInventorySlot(EntityLivingBase living, ItemStack stack, int itemSlot) {
        // 98/99 to sloty rak w konwencji replaceItemInInventory - rozumie je zarowno
        // EntityLivingBase, jak i EntityPlayer, wiec dziala tez dla mobow trzymajacych bron.
        if (living.getHeldItemMainhand() == stack) {
            return 98;
        }
        if (living.getHeldItemOffhand() == stack) {
            return 99;
        }
        if (living instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) living;
            if (itemSlot >= 0 && itemSlot < player.inventory.mainInventory.size()
                    && player.inventory.mainInventory.get(itemSlot) == stack) {
                return itemSlot;
            }
        }
        return -1;
    }

    /**
     * Postęp do ewolucji w tooltipie.
     *
     * <p>Natywne {@code WeaponToolMeleeBase} pokazuje surowy licznik. My pokazujemy też próg, bo
     * bez niego liczba nic nie mówi — gracz nie ma skąd wiedzieć, że celem jest 50 000.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        if (tier != ParasiteTier.LIVING) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        int kills = tag == null ? 0 : tag.getInteger("srpkills");
        tooltip.add(TextFormatting.DARK_AQUA + "---> " + kills + " / "
                + SRPConfig.weapon_livingSentient_HP_needed);
    }

    /**
     * Obrażenia i szybkość z {@code gear.nunchaku}, plus stałe spowolnienie tieru.
     *
     * <p>Sentient dostaje 0,778 NA WIERZCHU mnożnika z configu, bo Better Survival nie umie nadać
     * dwóm nunchaku różnych prędkości — {@code nunchakuSpd} jest globalne. Bez tego Sentient przy
     * podwojonych obrażeniach miałby DPS grubo ponad rodziną.
     */
    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, AttributeModifier> getAttributeModifiers(@Nonnull EntityEquipmentSlot slot,
            @Nonnull ItemStack stack) {
        Multimap<String, AttributeModifier> base = super.getAttributeModifiers(slot, stack);

        GearCategory.Nunchaku cfg = ModConfig.gear.nunchaku;
        double damageMultiplier = cfg.attackDamageMultiplier;
        double speedMultiplier = cfg.attackSpeedMultiplier * tier.getSpeedMultiplier();
        if (base == null || slot != EntityEquipmentSlot.MAINHAND
                || (damageMultiplier == 1.0D && speedMultiplier == 1.0D)) {
            return base;
        }

        String damageKey = SharedMonsterAttributes.ATTACK_DAMAGE.getName();
        String speedKey = SharedMonsterAttributes.ATTACK_SPEED.getName();
        Multimap<String, AttributeModifier> scaled = HashMultimap.create();
        for (Map.Entry<String, AttributeModifier> entry : base.entries()) {
            String key = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (damageMultiplier != 1.0D && damageKey.equals(key)) {
                scaled.put(key, withAmount(modifier, modifier.getAmount() * damageMultiplier));
            } else if (speedMultiplier != 1.0D && speedKey.equals(key)) {
                scaled.put(key, withAmount(modifier, scaleAttackSpeed(modifier.getAmount(), speedMultiplier)));
            } else {
                scaled.put(key, modifier);
            }
        }
        return scaled;
    }

    private static AttributeModifier withAmount(AttributeModifier source, double amount) {
        return new AttributeModifier(source.getID(), source.getName(), amount, source.getOperation());
    }

    /**
     * Szybkość ataku NIE jest w MC liczbą ataków na sekundę — to ujemna różnica względem bazowych
     * 4.0/s. Mnożnik ma dotyczyć prędkości KOŃCOWEJ, stąd (baza + różnica) * mnożnik - baza.
     * Przemnożenie samej różnicy dałoby odwrotność: mnożnik &gt; 1.0 robiłby broń WOLNIEJSZĄ.
     */
    private static double scaleAttackSpeed(double delta, double multiplier) {
        double base = SharedMonsterAttributes.ATTACK_SPEED.getDefaultValue();
        return (base + delta) * multiplier - base;
    }
}
