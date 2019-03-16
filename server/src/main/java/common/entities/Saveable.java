package common.entities;

/**
 *  This interface marks entities that are able to be saved to place depending on their specific *
 *
 *  An entity before ane after saving must be equal to itself
 * */
public interface Saveable {
    /**
     * Saves entity's properties, ensuring whether the procedure has been finished successfully or not
     * */
    boolean save();
}
