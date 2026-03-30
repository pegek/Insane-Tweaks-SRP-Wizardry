import java.lang.reflect.Field;
public class test_actions {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("electroblob.wizardry.item.SpellActions");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println(f.getName());
        }
    }
}
