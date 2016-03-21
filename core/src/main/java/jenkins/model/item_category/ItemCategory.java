package jenkins.model.item_category;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import hudson.model.TopLevelItemDescriptor;
import jenkins.model.Messages;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * A category for {@link hudson.model.Item}s.
 *
 * @since TODO
 */
public abstract class ItemCategory implements ModelObject, ExtensionPoint {

    public static int MIN_TOSHOW = 1;

    /**
     * Helpful to set the order.
     */
    private int weight = 1;

    /**
     * Identifier, e.g. "standaloneprojects", etc.
     *
     * @return the identifier
     */
    public abstract String getId();

    /**
     * The description in plain text
     *
     * @return the description
     */
    public abstract String getDescription();

    /**
     * Minimum number required to show the category.
     *
     * @return the minimum items required
     */
    public abstract int getMinToShow();

    protected void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * @return A integer with the weight.
     */
    public int getWeight() {
        return weight;
    }

    /**
     * A {@link ItemCategory} associated to this {@link TopLevelItemDescriptor}.
     *
     * @return A {@link ItemCategory}, if not found, {@link ItemCategory.UncategorizedCategory} is returned
     */
    @Nonnull
    public static ItemCategory getCategory(TopLevelItemDescriptor descriptor) {
        int weight = 1;
        ExtensionList<ItemCategory> categories = ExtensionList.lookup(ItemCategory.class);
        for (ItemCategory category : categories) {
            if (category.getId().equals(descriptor.getCategoryId())) {
                category.setWeight(categories.size() - weight);
                return category;
            }
            weight++;
        }
        return new UncategorizedCategory();
    }

    /**
     * The default {@link ItemCategory}, if an item doesn't belong anywhere else, this is where it goes by default.
     */
    @Extension(ordinal = Integer.MIN_VALUE)
    public static final class UncategorizedCategory extends ItemCategory {

        @Override
        public String getId() {
            return "uncategorized";
        }

        @Override
        public String getDescription() {
            return Messages.ItemCategory_Uncategorized_Description();
        }

        @Override
        public String getDisplayName() {
            return Messages.ItemCategory_Uncategorized_DisplayName();
        }

        @Override
        public int getMinToShow() {
            return ItemCategory.MIN_TOSHOW;
        }

    }

}
