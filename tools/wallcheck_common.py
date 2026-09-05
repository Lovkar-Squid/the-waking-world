SEE_THROUGH = {"air", "cave_air", "void_air", "grass", "short_grass", "tall_grass", "fern", "large_fern", "torch", "wall_torch", "lantern",
               "poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "red_tulip", "white_tulip", "pink_tulip", "orange_tulip", "allium",
               "lily_of_the_valley", "blue_orchid", "sunflower", "lilac", "rose_bush", "peony", "snow", "water", "iron_bars", "cyan_banner", "cyan_wall_banner",
               "glass_pane", "chain", "soul_lantern", "grass_block", "dirt", "dirt_path", "gravel", "cobblestone", "oak_leaves", "spruce_leaves"}
def see_through(name):
    return name in SEE_THROUGH
