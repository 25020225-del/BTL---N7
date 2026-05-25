package model.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemFactory - polymorphic instantiation tests")
class ItemFactoryTest {

    private static final String ID = "TEST-ID";
    private static final String NAME = "Test Product Name";
    private static final String DESC = "Test product description metadata.";
    private static final long START_PRICE = 500_000L;

    @Test
    @DisplayName("Should create TangibleItem when type is TANGIBLE (case-insensitive)")
    void createTangibleItem() {
        Item item = ItemFactory.createItem("TANGIBLE", ID, NAME, DESC, START_PRICE);
        
        assertNotNull(item);
        assertTrue(item instanceof TangibleItem);
        assertEquals(ItemFactory.TYPE_TANGIBLE, item.getType());
        assertEquals(ID, item.getId());
        assertEquals(NAME, item.getItemName());
        assertEquals(DESC, item.getDescription());
        assertEquals(START_PRICE, item.getStartingPrice());
        
        String info = item.getInfo();
        assertTrue(info.contains("[Tangible Asset]"));
        assertTrue(info.contains(NAME));
    }

    @Test
    @DisplayName("Should create DigitalItem when type is DIGITAL (case-insensitive)")
    void createDigitalItem() {
        Item item = ItemFactory.createItem("digital", ID, NAME, DESC, START_PRICE);
        
        assertNotNull(item);
        assertTrue(item instanceof DigitalItem);
        assertEquals(ItemFactory.TYPE_DIGITAL, item.getType());
        assertEquals(ID, item.getId());
        assertEquals(NAME, item.getItemName());
        assertEquals(DESC, item.getDescription());
        assertEquals(START_PRICE, item.getStartingPrice());
        
        String info = item.getInfo();
        assertTrue(info.contains("[Digital Product]"));
        assertTrue(info.contains(NAME));
    }

    @Test
    @DisplayName("Should create ServicePackage when type is SERVICE (case-insensitive)")
    void createServicePackage() {
        Item item = ItemFactory.createItem("SeRvIcE", ID, NAME, DESC, START_PRICE);
        
        assertNotNull(item);
        assertTrue(item instanceof ServicePackage);
        assertEquals(ItemFactory.TYPE_SERVICE, item.getType());
        assertEquals(ID, item.getId());
        assertEquals(NAME, item.getItemName());
        assertEquals(DESC, item.getDescription());
        assertEquals(START_PRICE, item.getStartingPrice());
        
        String info = item.getInfo();
        assertTrue(info.contains("[Service Package]"));
        assertTrue(info.contains(NAME));
    }

    @Test
    @DisplayName("Should default to TangibleItem when type is null")
    void createItemWithNullType() {
        Item item = ItemFactory.createItem(null, ID, NAME, DESC, START_PRICE);
        
        assertNotNull(item);
        assertTrue(item instanceof TangibleItem);
        assertEquals(ItemFactory.TYPE_TANGIBLE, item.getType());
    }

    @Test
    @DisplayName("Should default to TangibleItem when type is blank or empty")
    void createItemWithBlankType() {
        Item item1 = ItemFactory.createItem("", ID, NAME, DESC, START_PRICE);
        assertNotNull(item1);
        assertTrue(item1 instanceof TangibleItem);
        
        Item item2 = ItemFactory.createItem("   ", ID, NAME, DESC, START_PRICE);
        assertNotNull(item2);
        assertTrue(item2 instanceof TangibleItem);
    }

    @Test
    @DisplayName("Should default to TangibleItem when type is unknown")
    void createItemWithUnknownType() {
        Item item = ItemFactory.createItem("NFT_COLLECTIBLE", ID, NAME, DESC, START_PRICE);
        
        assertNotNull(item);
        assertTrue(item instanceof TangibleItem);
        assertEquals(ItemFactory.TYPE_TANGIBLE, item.getType());
    }
}
