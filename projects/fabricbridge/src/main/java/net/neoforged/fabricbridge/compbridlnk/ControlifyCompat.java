package net.neoforged.fabricbridge.compbridlnk;

/**
 * Controlify on the 26w14aCp26 line: its virtual-mouse snapping reads the recipe book's filter
 * button through an {@code @Accessor} on {@code RecipeBookComponent.filterButton}. 26w14a removed
 * that widget (the class still declares {@code getFilterButtonTextures()}, but no button field),
 * so the accessor finds no candidate and Mixin fails the whole config - which is required, and
 * takes the launch down. The accessor is dropped; the rest of Controlify keeps working, only the
 * snapping of the virtual mouse to the recipe book filter is lost on this base.
 */
public final class ControlifyCompat {
    private static final String CONTROLIFY_MIXINS = "controlify.mixins.json";
    private static final String RECIPE_BOOK_ACCESSOR = "feature.virtualmouse.snapping.RecipeBookComponentAccessor";

    private ControlifyCompat() {}

    /**
     * @return the filtered config text, or {@code null} when this is not the Controlify config or
     *         the accessor was not listed
     */
    public static String withoutRecipeBookFilterAccessor(String configText) {
        var filtered = configText.replaceAll(
                "(?m)^\\s*\"" + java.util.regex.Pattern.quote(RECIPE_BOOK_ACCESSOR) + "\"\\s*,?\\s*\\r?\\n", "");
        return filtered.equals(configText) ? null : filtered;
    }

    public static boolean isControlifyConfig(String entryName) {
        return CONTROLIFY_MIXINS.equals(entryName);
    }
}
