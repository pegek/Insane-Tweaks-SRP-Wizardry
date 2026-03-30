import java.lang.reflect.Field;
try {
    Class<?> clazz = Class.forName("com.existingeevee.swparasites.init.ParasiteSWProperties");
    for (Field f : clazz.getDeclaredFields()) {
        System.out.println(f.getName());
    }
    
    System.out.println("WeaponProps:");
    Class<?> clazz2 = Class.forName("com.oblivioussp.spartanweaponry.api.WeaponProperties");
    for (Field f : clazz2.getDeclaredFields()) {
        System.out.println(f.getName());
    }
} catch (Exception e) {
    e.printStackTrace();
}
